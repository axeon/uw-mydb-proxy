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
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.OkPacket;
import uw.mydb.proxy.sqlparse.SqlParseResult;
import uw.mydb.proxy.stats.StatsManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 前端代理的多节点汇聚处理器。
 * 为了高效处理数据，返回时候不对数据集进行任何处理。
 *
 * @author axeon
 */
public class ProxyMultiNodeHandler implements MySqlSessionCallback, Runnable {

    private static final Logger logger = LoggerFactory.getLogger( ProxyMultiNodeHandler.class );

    /**
     * 包阶段：初始。
     */
    private static final int PACKET_STEP_INIT = 0;

    /**
     * 包阶段：字段结束。
     */
    private static final int PACKET_STEP_EOF_FIELD = 1;

    /**
     * 包阶段：全部结束。
     */
    private static final int PACKET_STEP_EOF = 2;

    /**
     * 创建时间.
     */
    private final long createTime = SystemClock.now();

    /**
     * 客户端信息。
     */
    private String clientInfo;

    /**
     * 绑定的channel
     */
    private ChannelHandlerContext ctx;

    /**
     * 数据行计数。
     */
    private AtomicInteger dataRowsCount = new AtomicInteger();

    /**
     * 行数
     */
    private AtomicInteger affectRowsCount = new AtomicInteger();

    /**
     * 门栓
     */
    private CountDownLatch countDownLatch;

    /**
     * packet序列。
     */
    private AtomicInteger packetSeq = new AtomicInteger();

    /**
     * 是否已经进入data传输。
     */
    private AtomicInteger packetStep = new AtomicInteger( PACKET_STEP_INIT );

    /**
     * 错误计数。
     */
    private AtomicInteger errorCount = new AtomicInteger();

    /**
     * 第一个错误包。
     */
    private ErrorPacket errorPacket = null;

    /**
     * 要查询的mysqlGroups
     */
    private SqlParseResult parseResult;

    /**
     * 发送字节数。
     */
    private AtomicLong txBytes = new AtomicLong();

    /**
     * 是否执行失败了
     */
    private boolean isExeSuccess = true;


    public ProxyMultiNodeHandler(String clientInfo, ChannelHandlerContext ctx, SqlParseResult parseResult) {
        this.clientInfo = clientInfo;
        this.ctx = ctx;
        this.parseResult = parseResult;
        countDownLatch = new CountDownLatch( parseResult.getSqlInfoList().size() );
    }

    /**
     * 获取客户端信息。
     *
     * @return
     */
    @Override
    public String getClientInfo() {
        return clientInfo;
    }

    /**
     * 收到Ok数据包。
     *
     * @param buf
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
     * 收到Error数据包。
     *
     * @param buf
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
     * 收到ResultSetHeader数据包。
     *
     * @param buf
     */
    @Override
    public synchronized void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        if (packetStep.get() == PACKET_STEP_EOF_FIELD) {
            return;
        }
        if (packetSeq.compareAndSet( 0, packetId )) {
            txBytes.addAndGet( buf.readableBytes() );
            ctx.write( buf.retain() );
        }
    }

    /**
     * 收到FieldPacket数据包。
     *
     * @param buf
     */
    @Override
    public synchronized void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
        if (packetStep.get() == PACKET_STEP_EOF_FIELD) {
            return;
        }
        if (packetSeq.compareAndSet( packetId - 1, packetId )) {
            txBytes.addAndGet( buf.readableBytes() );
            ctx.write( buf.retain() );
        }
    }

    /**
     * 收到FieldEOFPacket数据包。
     *
     * @param buf
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
     * 收到RowDataPacket数据包。
     *
     * @param buf
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
     * 收到RowDataEOFPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
        //在最后汇总输出，可以不用管了。
    }

    /**
     * 错误提示。
     *
     * @param errorNo
     * @param info
     */
    @Override
    public void onMysqlFailMessage(int errorNo, String info) {
        isExeSuccess = false;
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.packetId = 1;
        errorPacket.errorNo = errorNo;
        errorPacket.message = info;
        errorPacket.writeToChannel( ctx );
        ctx.flush();
    }

    /**
     * 通知解绑定。
     */
    @Override
    public void onFinish() {
        countDownLatch.countDown();
    }

    @Override
    public void run() {
        for (SqlParseResult.SqlInfo sqlInfo : parseResult.getSqlInfoList()) {
            MySqlSession mySqlSession = MySqlClient.getMySqlSession( sqlInfo.getClusterId(), parseResult.isMasterQuery() );
            if (mySqlSession == null) {
                logger.warn( "无法找到合适的mysqlSession!" );
                continue;
            }
            mySqlSession.addCommand( this, sqlInfo.getDatabase(), sqlInfo.getTable(), sqlInfo.getNewSql(), parseResult.getSqlType() );
        }
        //等待最长180s
        try {
            countDownLatch.await( 180, TimeUnit.SECONDS );
        } catch (InterruptedException e) {
            logger.error( e.getLocalizedMessage(), e );
        }
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
                errorPacket.writeToChannel( ctx );
                txBytes.addAndGet( errorPacket.getPacketLength() );
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
