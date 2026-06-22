package uw.mydb.proxy.server;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.common.util.SystemClock;
import uw.mydb.proxy.constant.SQLType;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.mysql.MySqlSession;
import uw.mydb.proxy.mysql.MySqlSessionCallback;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.OkPacket;
import uw.mydb.proxy.sqlparse.SqlParseResult;
import uw.mydb.proxy.stats.StatsManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多节点查询的聚合处理器，由 {@link ProxySession#query} 在 SQL 被路由到多个后端节点时提交到
 * {@link ProxySession#getMultiNodeExecutor} 执行。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>同步等待：构造时根据目标节点数初始化 {@link CountDownLatch}，{@link #run} 在派发完全部命令后
 *       {@code await(180s)}，每个节点完成会回调 {@link #onFinish} 触发 countDown。</li>
 *   <li>packetStep 状态机：{@link #PACKET_STEP_INIT}(收 header/field) ->
 *       {@link #PACKET_STEP_EOF_FIELD}(字段定义结束，开始接 row) ->
 *       {@link #PACKET_STEP_EOF}(全部结束)。控制多节点结果集的 header/field 只透传一次。</li>
 *   <li>packetSeq 重写：多节点结果集需作为单个连续结果集返回给客户端，行数据包的 packetId 由
 *       {@link #packetSeq} 单调递增重写后写入前端 channel。</li>
 *   <li>列数一致性校验：第一个节点的 ResultSetHeader 透传后记录列数（{@link #expectedFieldCount}），
 *       后续节点 header 的列数不一致时记告警并构造 Error（行数据会错位）。</li>
 *   <li>超时防护：{@link #run} 中 180s 未全部完成时遍历 {@link #dispatchedSessions} 强制
 *       {@code forceClose} 未完成节点的后端连接，避免连接泄漏与延迟回调的野指针写入。</li>
 * </ul>
 * 线程安全：回调方法由后端不同 session 的 EventLoop 并发调用，使用 synchronized + AtomicInteger 保证 packet 序列与状态机的一致性。
 *
 * @author axeon
 */
public class ProxyMultiNodeHandler implements MySqlSessionCallback, Runnable {

    private static final Logger logger = LoggerFactory.getLogger( ProxyMultiNodeHandler.class );

    /**
     * packetStep 状态机：初始状态，尚未收到任何 FieldEOF。
     */
    private static final int PACKET_STEP_INIT = 0;

    /**
     * packetStep 状态机：已收到字段定义结束 EOF（FieldEOF），后续节点不再透传 header/field。
     */
    private static final int PACKET_STEP_EOF_FIELD = 1;

    /**
     * packetStep 状态机：全部结果集结束（此处实际未使用，由 run() 末尾根据 packetStep > INIT 判定）。
     */
    private static final int PACKET_STEP_EOF = 2;

    /**
     * 任务创建时间（毫秒），用于整体执行耗时统计。
     */
    private final long createTime = SystemClock.now();

    /**
     * 客户端标识（IP），用于日志与错误 SQL 上报。
     */
    private String clientInfo;

    /**
     * 前端 channel 上下文，聚合后的结果包通过它写回客户端。
     */
    private ChannelHandlerContext ctx;

    /**
     * 数据行计数（多节点累加）。
     */
    private AtomicInteger dataRowsCount = new AtomicInteger();

    /**
     * 受影响行计数（多节点 OK 包累加；初值 0 表示尚未收到 OK 包，-1 用作哨兵）。
     */
    private AtomicInteger affectRowsCount = new AtomicInteger();

    /**
     * 同步门栓：构造时按目标节点数初始化，每个节点 onFinish 时 countDown。
     */
    private CountDownLatch countDownLatch;

    /**
     * 写往客户端的 packetId 序列号（单调递增），用于把多节点结果集重写为一个连续的 MySQL 结果集。
     */
    private AtomicInteger packetSeq = new AtomicInteger();

    /**
     * packetStep 状态机当前值（INIT/EOF_FIELD/EOF）。
     */
    private AtomicInteger packetStep = new AtomicInteger( PACKET_STEP_INIT );

    /**
     * 错误节点计数（收到 ErrorPacket 或 fail message 时自增）。
     */
    private AtomicInteger errorCount = new AtomicInteger();

    /**
     * 第一个错误包（仅记录第一个，compareAndSet 保证只赋值一次），用于在 run() 末尾回传客户端。
     */
    private ErrorPacket errorPacket = null;

    /**
     * 期望的列数（来自第一个节点的 ResultSetHeader），用于多节点列数一致性校验。
     * -1 表示尚未收到第一个 header 或 lenenc 编码 >=251 时无法判定，跳过校验。
     */
    private int expectedFieldCount = -1;

    /**
     * 当前多节点查询的解析结果（含 sqlInfoList、源 SQL 等）。
     */
    private SqlParseResult parseResult;

    /**
     * 写往客户端的累计字节数，用于错误 SQL 上报。
     */
    private AtomicLong txBytes = new AtomicLong();

    /**
     * 本次多节点查询是否全部成功的标记。任一节点 Error / 列数不一致 / 超时均置为 false。
     */
    private boolean isExeSuccess = true;

    /**
     * 已派发的后端 session 集合，用于超时时强制关闭未回调的连接，避免连接泄漏。
     * key=MySqlSession 实例，onFinish 后并未实时移除（在 run() 末尾统一 clear），超时路径据此 forceClose。
     */
    private final ConcurrentHashMap<MySqlSession, Boolean> dispatchedSessions = new ConcurrentHashMap<>();


    /**
     * 构造多节点聚合处理器，初始化 {@link #countDownLatch} 为目标节点数。
     *
     * @param clientInfo  客户端 IP
     * @param ctx         前端 channel 上下文
     * @param parseResult SQL 解析结果（sqlInfoList.size() 决定门栓数量）
     */
    public ProxyMultiNodeHandler(String clientInfo, ChannelHandlerContext ctx, SqlParseResult parseResult) {
        this.clientInfo = clientInfo;
        this.ctx = ctx;
        this.parseResult = parseResult;
        countDownLatch = new CountDownLatch( parseResult.getSqlInfoList().size() );
    }

    /**
     * @return 客户端 IP（实现 {@link MySqlSessionCallback}）
     */
    @Override
    public String getClientInfo() {
        return clientInfo;
    }

    /**
     * 后端回调：收到 OK 包。累加 affectedRows；若该节点 affectedRows==0，则将 affectRowsCount 从 -1 推进到 0 标记收到过 OK。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      OK 包 ByteBuf
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        OkPacket okPacket = new OkPacket();
        okPacket.readPayLoad( buf );
        if (okPacket.affectedRows > 0) {
            affectRowsCount.addAndGet( (int)okPacket.affectedRows );
        } else {
            affectRowsCount.compareAndSet( -1, 0 );
        }
    }

    /**
     * 后端回调：收到 Error 包。仅记录第一个错误包（CAS 保证只赋值一次），并自增错误计数；最终错误由 {@link #run} 统一回传。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      Error 包 ByteBuf
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        if (errorCount.compareAndSet( 0, 1 )) {
            this.errorPacket = new ErrorPacket();
            errorPacket.readPayLoad( buf );
        }
        errorCount.incrementAndGet();
    }

    /**
     * 后端回调：收到 ResultSetHeader。
     * <p>
     * 第一个节点的 header 透传给客户端并记录列数；后续节点的 header 校验列数一致性，
     * 不一致则记告警并构造 Error（行数据会按首节点列定义透传，列数错位会破坏客户端解码）。
     * 若 packetStep 已进入 EOF_FIELD，直接丢弃后续 header。
     * <p>
     * 方法加 synchronized：多节点回调来自不同 EventLoop 线程，需保证 packetSeq CAS 与 ctx.write 的原子性。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      header 包 ByteBuf（含 4 字节包头 + payload）
     */
    @Override
    public synchronized void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        if (packetStep.get() == PACKET_STEP_EOF_FIELD) {
            return;
        }
        //读取fieldCount（lenenc int，在跳过4字节包头后的payload首字节起）用于一致性校验。
        //buf经readPayLoad后readerIndex已在包头之后。
        int fieldCount = readFieldCountFromHeader(buf);
        if (expectedFieldCount < 0) {
            //第一个节点的header，记录列数并透传。
            expectedFieldCount = fieldCount;
            if (packetSeq.compareAndSet( 0, packetId )) {
                txBytes.addAndGet( buf.readableBytes() );
                ctx.write( buf.retain() );
            }
        } else {
            //后续节点的header：校验列数一致性，不一致则记录告警（数据已按首节点列定义透传，列数不一致会导致行数据错位）。
            if (fieldCount != expectedFieldCount) {
                logger.warn( "多节点查询列数不一致！期望[{}]实际[{}]，行数据可能错位，client={}", expectedFieldCount, fieldCount, clientInfo );
                isExeSuccess = false;
                if (errorPacket == null) {
                    errorPacket = new ErrorPacket();
                    errorPacket.packetId = 1;
                    errorPacket.errorNo = MySqlErrorCode.ERR_ROUTE_CALC;
                    errorPacket.message = "Multi-node schema mismatch! expected " + expectedFieldCount + " columns but got " + fieldCount;
                }
            }
        }
    }

    /**
     * 从 ResultSetHeader 包读取 fieldCount（列数）。
     * <p>
     * buf 包含完整包（4 字节包头 + payload），readerIndex 在包头起始处。
     * fieldCount 是 lenenc int 编码，位于 payload 首字节（即 buf 的第 5 字节）。
     * 仅处理 firstByte &lt; 0xFB 的简单情形；大列数（lenenc &gt;=251）返回 -1 表示不校验。
     *
     * @param buf 完整包 ByteBuf
     * @return 列数，或 -1 表示无法判定
     */
    private int readFieldCountFromHeader(ByteBuf buf) {
        try {
            int payloadStart = buf.readerIndex() + 4;
            if (payloadStart >= buf.writerIndex()) {
                return -1;
            }
            int firstByte = buf.getByte( payloadStart ) & 0xFF;
            if (firstByte < 0xFB) {
                return firstByte;
            }
            //大列数场景（lenenc编码>=251），简化处理返回-1表示不校验。
            return -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    /**
     * 后端回调：收到字段定义包。仅在 INIT 状态透传第一个节点的字段定义；EOF_FIELD 后丢弃。
     * 通过 packetSeq CAS 保证只接受连续序号的包，乱序则告警丢弃。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      字段定义包 ByteBuf
     */
    @Override
    public synchronized void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
        if (packetStep.get() == PACKET_STEP_EOF_FIELD) {
            return;
        }
        if (packetSeq.compareAndSet( packetId - 1, packetId )) {
            txBytes.addAndGet( buf.readableBytes() );
            ctx.write( buf.retain() );
        } else {
            logger.warn( "receiveFieldDataPacket丢弃数据包: 期望packetSeq={}, 实际packetId={}", packetSeq.get(), packetId );
        }
    }

    /**
     * 后端回调：收到字段定义结束的 EOF 包。CAS 将 packetStep 从 INIT 推进到 EOF_FIELD（只生效一次），透传该 EOF 后递增 packetSeq。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      EOF 包 ByteBuf
     */
    @Override
    public synchronized void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf) {
        if (packetStep.compareAndSet( PACKET_STEP_INIT, PACKET_STEP_EOF_FIELD )) {
            txBytes.addAndGet( buf.readableBytes() );
            ctx.write( buf.retain() );
            packetSeq.incrementAndGet();
        }
    }

    /**
     * 后端回调：收到行数据包。累加行计数，重写 packetId（buf 第 4 字节）为 packetSeq 递增值后透传前端，
     * 使多节点行数据在客户端看来是一个连续的结果集。
     *
     * @param packetId 原始 packetId（会被重写）
     * @param buf      行数据包 ByteBuf
     */
    @Override
    public synchronized void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        txBytes.addAndGet( buf.readableBytes() );
        dataRowsCount.incrementAndGet();
        packetId = (byte) (packetSeq.incrementAndGet());
        buf.setByte( 3, packetId );
        ctx.write( buf.retain() );
    }

    /**
     * 后端回调：收到行数据结束 EOF 包。空实现：最终 EOF/OK 包由 {@link #run} 在所有节点收尾后统一输出，
     * 这里不做处理避免与 {@code run()} 末尾产生双重写入。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      EOF 包 ByteBuf
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
        //在最后汇总输出，可以不用管了。
    }

    /**
     * 后端回调：收到非 ErrorPacket 形式的失败信息（如连接异常）。
     * <p>
     * 多节点场景下：仅记录第一个错误信息，不立即写入客户端。
     * 最终的错误包由 {@link #run} 在所有节点收尾后统一输出，避免与 {@code run()} 末尾的包产生双重写入。
     *
     * @param errorNo MySQL 错误号
     * @param info    错误信息
     */
    @Override
    public void onMysqlFailMessage(int errorNo, String info) {
        isExeSuccess = false;
        if (errorPacket == null) {
            errorPacket = new ErrorPacket();
            errorPacket.packetId = 1;
            errorPacket.errorNo = errorNo;
            errorPacket.message = info;
        }
        errorCount.incrementAndGet();
    }

    /**
     * 后端回调：单节点命令完成。将 {@link #countDownLatch} 减 1，全部完成时唤醒 {@link #run} 中的 await。
     */
    @Override
    public void onFinish() {
        countDownLatch.countDown();
    }

    /**
     * 多节点查询执行主体，由 {@link ProxySession#query} 提交到异步线程池调用。
     * <p>
     * 流程：
     * <ol>
     *   <li>遍历 sqlInfoList 逐个获取后端 session、记录到 {@link #dispatchedSessions}、下发命令；获取失败则直接 countDown。</li>
     *   <li>{@code countDownLatch.await(180s)} 等待全部回调。</li>
     *   <li>超时未完成：遍历 dispatchedSessions 调用 {@code forceClose} 强制关闭未完成的后端连接，并构造 timeout Error。</li>
     *   <li>根据 packetStep 输出收尾包：已进入结果集（packetStep > INIT）则补写 EOF/OK；否则按 OK / Error / 默认 Error 回传。</li>
     *   <li>失败时上报错误 SQL，最后 {@code ctx.flush()} 一次性把积攒的包发往前端。</li>
     * </ol>
     */
    @Override
    public void run() {
        for (SqlParseResult.SqlInfo sqlInfo : parseResult.getSqlInfoList()) {
            MySqlSession mySqlSession = MySqlClient.getMySqlSession( sqlInfo.getClusterId(), parseResult.isMasterQuery() );
            if (mySqlSession == null) {
                logger.warn( "无法找到合适的mysqlSession!" );
                countDownLatch.countDown();
                continue;
            }
            dispatchedSessions.put( mySqlSession, Boolean.TRUE );
            mySqlSession.addCommand( this, sqlInfo.getDatabase(), sqlInfo.getTable(), sqlInfo.getNewSql(), parseResult.getSqlType() );
        }
        //等待最长180s
        boolean completed;
        try {
            completed = countDownLatch.await( 180, TimeUnit.SECONDS );
        } catch (InterruptedException e) {
            logger.error( e.getLocalizedMessage(), e );
            completed = false;
        }
        //超时未完成的节点，强制关闭后端连接，避免连接泄漏与延迟回调的野指针写入。
        if (!completed) {
            logger.warn( "多节点查询超时(180s)，强制关闭{}个未完成的后端连接，client={}", dispatchedSessions.size(), clientInfo );
            for (MySqlSession session : dispatchedSessions.keySet()) {
                try {
                    session.forceClose();
                } catch (Throwable e) {
                    logger.warn( "强制关闭后端连接失败: {}", e.getMessage() );
                }
            }
            isExeSuccess = false;
            if (errorPacket == null) {
                errorPacket = new ErrorPacket();
                errorPacket.packetId = 1;
                errorPacket.errorNo = MySqlErrorCode.ERR_CONN_NOT_ALIVE;
                errorPacket.message = "Multi-node query timeout!";
            }
        }
        dispatchedSessions.clear();
        //如下代码必须要异步线程中跑，否则会出问题。
        //开始返回最后的包。
        if (packetStep.get() > PACKET_STEP_INIT) {
            //输出eof包。
            OkPacket eofPacket = new OkPacket();
            eofPacket.packetId = (byte) packetSeq.incrementAndGet();
            eofPacket.warningCount = errorCount.get();
            eofPacket.serverStatus = 0x22;
            eofPacket.writeToChannel( ctx );
            txBytes.addAndGet( eofPacket.getPacketLength() );
        } else {
            if (affectRowsCount.get() > -1) {
                //说明有ok包。
                OkPacket okPacket = new OkPacket();
                okPacket.packetId = 1;
                okPacket.affectedRows = affectRowsCount.get();
                okPacket.warningCount = errorCount.get();
                okPacket.writeToChannel( ctx );
                txBytes.addAndGet( okPacket.getPacketLength() );
            } else {
                //说明全部就是错误包啦，直接返回第一個error包
                if (errorPacket != null) {
                    errorPacket.writeToChannel( ctx );
                    txBytes.addAndGet( errorPacket.getPacketLength() );
                } else {
                    ErrorPacket defaultError = new ErrorPacket();
                    defaultError.packetId = 1;
                    defaultError.errorNo = MySqlErrorCode.ERR_CONN_NOT_ALIVE;
                    defaultError.message = "All nodes returned error!";
                    defaultError.writeToChannel( ctx );
                    txBytes.addAndGet( defaultError.getPacketLength() );
                }
                isExeSuccess = false;
            }
        }
        //开始统计数据了。
        if (!isExeSuccess) {
            long now = SystemClock.now();
            StatsManager.reportErrorSql( this.clientInfo, 0, 0, parseResult.getSourceDatabase(), null, parseResult.getSourceSql(), SQLType.OTHER.getValue(),
                    Math.max( dataRowsCount.get(), this.affectRowsCount.get() ), 0, txBytes.get(), now - createTime, now, 0, "Proxy Multi Node Error!", null );
        }
        ctx.flush();
    }
}
