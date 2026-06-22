package uw.mydb.proxy.mysql;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import uw.common.util.SystemClock;
import uw.mydb.common.conf.MysqlServerConfig;
import uw.mydb.proxy.constant.SQLType;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.protocol.constant.MySQLCapability;
import uw.mydb.proxy.protocol.packet.*;
import uw.mydb.proxy.stats.StatsManager;
import uw.mydb.proxy.util.CachingSha2PasswordPlugin;
import uw.mydb.proxy.util.MySqlNativePasswordPlugin;

import java.security.NoSuchAlgorithmException;

/**
 * MySQL 后端单连接会话实例，负责协议握手鉴权、命令收发、结果集分片回调。
 *
 * <p>每个 session 绑定一个 Netty {@link Channel}，由 {@link MysqlPoolHandler#channelCreated} 创建并
 * 挂载到 channel 的 {@link MySqlHandler#MYSQL_SESSION} attribute 上；同一时刻一个 session 只能服务
 * 一个前端请求（通过 {@code sessionCallback} 单一回调对象实现串行化）。
 *
 * <h3>sessionStatus 状态机</h3>
 * <pre>
 *   SESSION_INIT(0) ──握手包──&gt; SESSION_AUTH(1) ──Ok包──&gt; SESSION_USING(3) ──命令结束──&gt; SESSION_NORMAL(2)
 *                                            │                                              │
 *                                            └─失败/异常─&gt; SESSION_CLOSED(-1)              └─新命令──&gt; SESSION_USING(3)
 * </pre>
 * <ul>
 *   <li>{@link #SESSION_INIT}：channel 刚建连，尚未与后端完成握手，不可用；</li>
 *   <li>{@link #SESSION_AUTH}：已发出首包，等待后端验证（含 AuthSwitch / caching_sha2 full auth）；</li>
 *   <li>{@link #SESSION_NORMAL}：闲置，可被 acquire 接受新命令；</li>
 *   <li>{@link #SESSION_USING}：正在执行一条命令，收到响应后回到 SESSION_NORMAL；</li>
 *   <li>{@link #SESSION_CLOSED}：终态，channel 关闭、从 pool 释放，不可再使用。</li>
 * </ul>
 *
 * <h3>鉴权握手流程</h3>
 * <ol>
 *   <li>{@link #handleHandshake}：读后端 Initial Handshake Packet，按 server 给出的 plugin 名
 *       （caching_sha2_password 或 native_password）构造 scramble 密码并发出 HandshakeResponse；</li>
 *   <li>{@link #handleAuthResponse}：处理后端响应。
 *     <ul>
 *       <li>{@code AuthSwitchRequest}：切换 plugin，重新 scramble 并回复；</li>
 *       <li>{@code AuthMoreData}：caching_sha2 流程。data=0x04 时请求公钥（发 0x02），data=0x01 时
 *           进入 {@link #handleCachingSha2FullAuth} 用服务器公钥 RSA 加密密码；data=0x03 fast-auth
 *           直接等 OK 包；</li>
 *       <li>{@code OkPacket}：鉴权成功，状态转入 SESSION_USING 并触发 {@link #execute()}；</li>
 *       <li>{@code ErrorPacket}：失败 trueClose。</li>
 *     </ul></li>
 * </ol>
 *
 * <h3>命令响应处理</h3>
 * 由 {@link #handleCommandResponse} 按 resultStatus 子状态机推进（RESULT_INIT→RESULT_FIELD→RESULT_DATA），
 * 将 Ok/Error/ResultSetHeader/Field/Row 各类包逐个回调到 {@link #sessionCallback}。命令结束后
 * {@link #unbindCallback()} 统一进行 SQL 统计、状态归零、释放 channel 给 pool。
 *
 * <h3>线程安全模型</h3>
 * session 的所有读写几乎都发生在 channel 归属的 EventLoop 线程上（由 {@link MySqlHandler} 调用
 * {@link #handleResponse}）；仅 {@link #sessionStatus}、{@link #resultStatus}、计数器字段标记为
 * volatile 以保证少量跨线程可见性（housekeeping 读取 lastRequestTime）。session 不支持被多个
 * 前端并发复用，回调对象单一。
 *
 * @author axeon
 */
public class MySqlSession {

    /**
     * logger
     */
    private static final org.slf4j.Logger log = LoggerFactory.getLogger( MySqlSession.class );

    /**
     * 终态：channel 已关闭，不可再用。trueClose 后置此值。
     */
    private static final int SESSION_CLOSED = -1;

    /**
     * 初始状态：channel 刚建连，尚未完成后端握手。此状态不可用。
     */
    private static final int SESSION_INIT = 0;

    /**
     * 验证中状态：已发出 HandshakeResponse，等待后端 Ok/AuthSwitch/AuthMoreData。此状态不可用。
     */
    private static final int SESSION_AUTH = 1;

    /**
     * 正常闲置状态：可被 acquire 接受新命令。绑定 callback 时自动转入 USING。
     */
    private static final int SESSION_NORMAL = 2;

    /**
     * 使用中状态：正在执行命令，命令响应完成后回到 NORMAL。
     */
    private static final int SESSION_USING = 3;

    /**
     * 命令结果集初始状态：尚未收到 ResultSetHeader。
     */
    private static final int RESULT_INIT = 0;

    /**
     * 命令结果集列状态：已收到 header，正在接收 field 定义包。
     */
    private static final int RESULT_FIELD = 1;

    /**
     * 命令结果集数据状态：field 区结束，正在接收 row 数据包。
     */
    private static final int RESULT_DATA = 2;

    /**
     * session 创建时间（ms，{@link SystemClock#now()}），用于 pool housekeeping 的 maxAge 判断。
     */
    private final long createTime = SystemClock.now();

    /**
     * 最近一次请求开始时间（ms）。bindCallback 时更新为当前时间，unbindCallback 时再次更新；
     * pool 据此判断 idle/busy 超时。
     */
    private long lastRequestTime = createTime;

    /**
     * 当前绑定的前端回调对象。一个 session 同一时刻只绑定一个 callback，保证命令串行化。
     * 命令结束时由 unbindCallback 置 null。
     */
    private MySqlSessionCallback sessionCallback;

    /**
     * 归属连接池。在 {@link MySqlClient#getMySqlSession} 取出 session 后通过
     * {@link #bindChannelPool(MySqlPool)} 注入，unbindCallback / trueClose 时据此归还 channel。
     */
    private MySqlPool channelPool = null;

    /**
     * 对应的后端 channel。生命周期与 session 一致。
     */
    private Channel channel;

    /**
     * 后端 MySQL 服务器配置（含 host/port/账号密码/clusterId 等），鉴权与统计时使用。
     */
    private MysqlServerConfig mysqlServerConfig;

    /**
     * 鉴权 seed（来自后端握手包 auth_plugin_data 拼接），caching_sha2_password full-auth 流程
     * 用它参与 RSA 加密密码。
     */
    private byte[] authSeed;

    /**
     * 当前协商出的鉴权 plugin 名（caching_sha2_password / mysql_native_password），
     * full-auth 公钥响应分支据此判定。
     */
    private String authPluginName;

    /**
     * 连接状态，取值为 SESSION_* 常量。volatile 保证 housekeeping 线程读到最新值。
     */
    private volatile int sessionStatus = SESSION_INIT;

    /**
     * 结果集接收子状态，取值为 RESULT_* 常量，仅在 SESSION_USING 下有效。volatile 同上。
     */
    private volatile int resultStatus = RESULT_INIT;


    /**
     * 当前执行 SQL 所在的库名（用于统计），从 addCommand 入参透传，可为 null。
     */
    private String database;

    /**
     * 当前执行 SQL 所在的表名（用于统计），从 addCommand 入参透传，可为 null。
     */
    private String table;

    /**
     * 当前执行的 SQL 文本，unbindCallback 后置 null。
     */
    private String sql;

    /**
     * 当前 SQL 类型，见 {@link SQLType}。unbindCallback 后重置为 {@link SQLType#OTHER}。
     */
    private int sqlType;


    /**
     * 当前命令已接收的数据行计数（行数），用于统计。unbind 时清零。
     */
    private int dataRowsCount;

    /**
     * 当前命令受影响行计数（来自 OkPacket.affectedRows），用于统计。unbind 时清零。
     */
    private int affectRowsCount;

    /**
     * 当前命令发送字节数（写向后端的 CommandPacket 长度）。unbind 时清零。
     */
    private long txBytes;

    /**
     * 当前命令接收字节数（读自后端的响应包长度累计）。unbind 时清零。
     */
    private long rxBytes;

    /**
     * 当前命令是否已成功（未收到 ErrorPacket）。初始 true，遇到 ErrorPacket 置 false。
     */
    private boolean isSuccess = true;

    /**
     * 当前结果集列数（来自 ResultSetHeaderPacket.fieldCount），驱动 RESULT_FIELD→RESULT_DATA 转换。
     */
    private int resultFieldCount = 0;

    /**
     * 当前结果集已接收的 field 包数量计数器。达到 {@link #resultFieldCount} 时转入 RESULT_DATA。
     */
    private int resultFieldPos = 0;


    /**
     * 构造 session，由 {@link MysqlPoolHandler#channelCreated} 在 channel 建连时调用。
     * 初始 sessionStatus 为 {@link #SESSION_INIT}，等待后端首包触发握手流程。
     *
     * @param mysqlServerConfig 后端服务器配置
     * @param channel           对应的 Netty channel
     */
    protected MySqlSession(MysqlServerConfig mysqlServerConfig, Channel channel) {
        this.mysqlServerConfig = mysqlServerConfig;
        this.channel = channel;
    }

    /**
     * 异步执行一条 SQL（便捷重载，无 database/table 信息，sqlType 默认 OTHER）。
     * 仅在 session 处于 SESSION_NORMAL/SESSION_USING 时可用，否则视为鉴权完成后自动触发。
     *
     * @param sessionCallback 前端回调
     * @param sql             待执行 SQL
     */
    public void addCommand(MySqlSessionCallback sessionCallback, String sql) {
        addCommand( sessionCallback, null, null, sql, SQLType.OTHER.getValue() );
    }

    /**
     * 异步执行一条 SQL（完整入参，携带 database/table/sqlType 用于统计）。
     * <p>绑定 callback 后，若 sessionStatus 已超过 SESSION_NORMAL（即鉴权刚完成的 SESSION_USING
     * 或闲置中再次使用），立即调用 {@link #execute()} 发出 COM_QUERY；
     * 否则 execute() 会在握手 Ok 包返回时由 {@link #handleAuthResponse} 触发。
     *
     * @param sessionCallback 前端回调
     * @param database        SQL 所在库名（用于统计，可 null）
     * @param table           SQL 所在表名（用于统计，可 null）
     * @param sql             待执行 SQL
     * @param sqlType         SQL 类型，见 {@link SQLType}
     */
    public void addCommand(MySqlSessionCallback sessionCallback, String database, String table, String sql, int sqlType) {
        bindCallback( sessionCallback );
        this.database = database;
        this.table = table;
        this.sql = sql;
        this.sqlType = sqlType;
        if (sessionStatus > SESSION_NORMAL) {
            execute();
        }
    }

    /**
     * @return session 创建时间（ms）
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * @return 最近一次请求开始时间（ms），供 pool housekeeping 判断超时
     */
    public long getLastRequestTime() {
        return lastRequestTime;
    }

    /**
     * 向当前回调转发 MySQL 错误号与消息（不改变 session 状态）。
     *
     * @param errorNo MySQL 错误号
     * @param info    错误文本
     */
    protected void failMessage(int errorNo, String info) {
        if (sessionCallback != null) {
            sessionCallback.onMysqlFailMessage( errorNo, info );
        }
    }

    /**
     * 绑定归属连接池。由 {@link MySqlClient#getMySqlSession} 在从 pool acquire channel 后注入，
     * 后续 unbindCallback/trueClose 据此调用 {@link MySqlPool#release(Channel)} 归还。
     *
     * @param channelPool 归属连接池
     */
    protected void bindChannelPool(MySqlPool channelPool) {
        this.channelPool = channelPool;
    }

    /**
     * 入口分发：根据 sessionStatus 路由到对应的协议处理方法。
     * <ul>
     *   <li>SESSION_INIT → {@link #handleHandshake} 处理后端握手包；</li>
     *   <li>SESSION_AUTH → {@link #handleAuthResponse} 处理鉴权响应；</li>
     *   <li>SESSION_USING → {@link #handleCommandResponse} 处理命令响应；</li>
     *   <li>SESSION_NORMAL / SESSION_CLOSED：非预期包，记 WARN 并关闭连接。</li>
     * </ul>
     * 长度防御：buf 不足 5 字节（4 包头 + 1 status）直接 failMessage + trueClose，避免 IndexOutOfBounds。
     *
     * @param ctx Netty 上下文
     * @param buf 后端响应包（含 4 字节包头）
     */
    protected void handleResponse(ChannelHandlerContext ctx, ByteBuf buf) {
        //长度防御：MySQL响应包至少5字节（4字节包头+1字节payload首字节status），短包直接关闭避免IndexOutOfBoundsException。
        if (buf == null || buf.readableBytes() < 5) {
            log.warn( "MySQL响应包长度异常[{}字节]，关闭连接！", buf == null ? 0 : buf.readableBytes() );
            failMessage( MySqlErrorCode.ERR_CONN_NOT_ALIVE, "MySQL response packet too short!" );
            trueClose();
            return;
        }
        switch (sessionStatus) {
            case MySqlSession.SESSION_INIT:
                //初始阶段，此时需要发送验证包
                handleHandshake( ctx, buf );
                break;
            case MySqlSession.SESSION_AUTH:
                //验证阶段。
                handleAuthResponse( ctx, buf );
                break;
            case MySqlSession.SESSION_NORMAL:
                //闲置idle接收到的信息
                log.warn( "!!!状态[SESSION_NORMAL]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                break;
            case MySqlSession.SESSION_USING:
                //开始接受业务数据。
                handleCommandResponse( ctx, buf );
                break;
            case MySqlSession.SESSION_CLOSED:
                //验证失败信息，直接关闭链接吧。
                log.warn( "!!!状态[SESSION_CLOSED]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                ctx.close();
                break;
            default:
                log.warn( "!!!状态[" + sessionStatus + "]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                ctx.close();
                //这时候基本上就是登录失败了，直接关连接就好了。
        }

    }

    /**
     * 真正关闭连接：置 SESSION_CLOSED 终态、关闭 channel、向 channelPool 归还 channel。
     * 幂等——若已 CLOSED 直接返回。此方法由 session 自身在协议异常/鉴权失败时调用，也由
     * {@link MySqlHandler#channelInactive} 在对端断开时调用。
     */
    protected void trueClose() {
        if (sessionStatus == SESSION_CLOSED) {
            return;
        }
        sessionStatus = SESSION_CLOSED;
        this.channel.close();
        try {
            if (this.channelPool != null) {
                this.channelPool.release( this.channel );
            }
        } catch (Throwable e) {
            log.error( e.getMessage(), e );
        }
    }

    /**
     * 强制关闭连接（公开入口），用于上层在超时、取消或主动断开场景下避免连接泄漏。
     * 内部直接委托 {@link #trueClose()}。
     */
    public void forceClose() {
        trueClose();
    }

    /**
     * 处理后端 Initial Handshake Packet（SESSION_INIT 阶段）。
     * <p>读到 ErrorPacket 直接关闭；否则解析握手包，按 server 给出的 plugin 名构造
     * {@link AuthHandshakeResponsePacket}（设置 client flags、16MB 包大小上限、username、password、
     * authPluginName），写出并 flush，sessionStatus 转入 SESSION_AUTH。
     * 此处缓存 authSeed/authPluginName 以备 caching_sha2 full-auth 使用。
     *
     * @param ctx Netty 上下文
     * @param buf 后端握手包
     */
    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
        byte status = buf.getByte( 4 );
        if (status == MySqlPacket.PACKET_ERROR) {
            ErrorPacket errorPacket = new ErrorPacket();
            errorPacket.readPayLoad( buf );
            log.error( "MySQL[{}]服务器握手阶段报错{}:{}", mysqlServerConfig.toString(), errorPacket.errorNo, errorPacket.message );
            //报错了，直接关闭吧。
            sessionStatus = SESSION_CLOSED;
            trueClose();
            return;
        }
        AuthHandshakeRequestPacket handshakePacket = new AuthHandshakeRequestPacket();
        handshakePacket.readPayLoad( buf );
        // 设置字符集编码
        int charsetIndex = (handshakePacket.serverCharsetIndex & 0xff);
        // 发送应答报文给后端
        AuthHandshakeResponsePacket handshakeResponsePacket = new AuthHandshakeResponsePacket();
        handshakeResponsePacket.packetId = 1;
        handshakeResponsePacket.clientCapability = MySQLCapability.initClientFlags();
        handshakeResponsePacket.maxPacketSize = 16 * 1024 * 1024;
        handshakeResponsePacket.charsetIndex = charsetIndex;
        handshakeResponsePacket.username = mysqlServerConfig.getUsername();
        handshakeResponsePacket.authPluginName = StringUtils.isNotBlank( handshakePacket.authPluginName ) ? handshakePacket.authPluginName :
                MySqlNativePasswordPlugin.PROTOCOL_PLUGIN_NAME;
        //缓存seed和plugin名，供caching_sha2_password full-auth流程使用。
        this.authSeed = buildAuthSeed( handshakePacket );
        this.authPluginName = handshakeResponsePacket.authPluginName;
        handshakeResponsePacket.password = buildPassword( mysqlServerConfig.getPassword(), handshakeResponsePacket.authPluginName, this.authSeed );
        handshakeResponsePacket.writeToChannel( ctx );
        ctx.flush();
        //进入验证模式。
        sessionStatus = SESSION_AUTH;
    }

    /**
     * 处理 SESSION_AUTH 阶段后端返回的鉴权响应包。
     * <p>按 status 字节分发：
     * <ul>
     *   <li>{@link MySqlPacket#PACKET_AUTH_SWITCH}：后端要求切换 plugin，重新 scramble 密码并回复；</li>
     *   <li>{@link MySqlPacket#PACKET_AUTH_MORE_DATA}：caching_sha2_password 流程的中间数据。
     *       data=0x03 fast-auth 成功等 OK；data=0x04 请求公钥（发 0x02 单字节）；data=0x01 收到公钥，
     *       进入 {@link #handleCachingSha2FullAuth}；</li>
     *   <li>{@link MySqlPacket#PACKET_OK}：鉴权成功，进入 SESSION_USING 并触发 {@link #execute()}；</li>
     *   <li>{@link MySqlPacket#PACKET_ERROR}：鉴权失败，trueClose。</li>
     * </ul>
     *
     * @param ctx Netty 上下文
     * @param buf 后端响应包
     */
    private void handleAuthResponse(ChannelHandlerContext ctx, ByteBuf buf) {
        byte status = buf.getByte( 4 );
        switch (status) {
            case MySqlPacket.PACKET_AUTH_SWITCH:
                AuthSwitchRequestPacket authSwitchRequestPacket = new AuthSwitchRequestPacket();
                authSwitchRequestPacket.readPayLoad( buf );
                AuthSwitchResponsePacket authSwitchResponsePacket = new AuthSwitchResponsePacket();
                authSwitchResponsePacket.packetId = ++authSwitchRequestPacket.packetId;
                authSwitchResponsePacket.data = buildPassword( mysqlServerConfig.getPassword(), authSwitchRequestPacket.authPluginName, authSwitchRequestPacket.authPluginData );
                authSwitchResponsePacket.writeToChannel( ctx );
                ctx.flush();
                break;
            case MySqlPacket.PACKET_AUTH_MORE_DATA:
                AuthMoreDataPacket authMoreDataPacket = new AuthMoreDataPacket();
                authMoreDataPacket.readPayLoad( buf );
                //data 0x03: fast auth成功，等待OK包。
                //data 0x04: 需要full auth（无TLS场景需请求公钥+RSA加密密码）。
                //data 0x01: 公钥数据（full auth流程的响应）。
                if (authMoreDataPacket.data == 0x04) {
                    //请求服务器公钥：发送单字节0x02。
                    ByteBuf keyReq = ctx.alloc().buffer( 5 );
                    keyReq.writeMediumLE( 1 );
                    keyReq.writeByte( authMoreDataPacket.packetId + 1 );
                    keyReq.writeByte( 0x02 );
                    ctx.writeAndFlush( keyReq );
                } else if (authMoreDataPacket.data == 0x01 && CachingSha2PasswordPlugin.PROTOCOL_PLUGIN_NAME.equals( this.authPluginName )) {
                    //收到公钥，用RSA加密密码并发送。
                    handleCachingSha2FullAuth( ctx, buf, authMoreDataPacket.packetId );
                }
                //data==0x03时什么都不做，等待后续OK包。
                break;
            case MySqlPacket.PACKET_OK:
                OkPacket okPacket = new OkPacket();
                okPacket.readPayLoad( buf );
                sessionStatus = SESSION_USING;
                execute();
                break;
            case MySqlPacket.PACKET_ERROR:
                //报错了，直接关闭吧。
                ErrorPacket errorPacket = new ErrorPacket();
                errorPacket.readPayLoad( buf );
                log.error( "MySQL[{}]服务器验证阶段报错{}:{}", mysqlServerConfig.toString(), errorPacket.errorNo, errorPacket.message );
                sessionStatus = SESSION_CLOSED;
                trueClose();
                break;
            default:
                log.warn( "收到未知的登录数据包！status={}", status );
        }
    }

    /**
     * 处理caching_sha2_password的full auth流程。
     * 收到服务器公钥后，用RSA加密密码并发送。
     *
     * @param ctx
     * @param buf       完整的AuthMoreData响应包（含4字节包头），readPayLoad后readerIndex已在status+data之后。
     * @param packetId  当前包的packetId，响应包的packetId需+1。
     */
    private void handleCachingSha2FullAuth(ChannelHandlerContext ctx, ByteBuf buf, byte packetId) {
        try {
            //buf经readPayLoad后readerIndex已在status(1)+data(1)之后，剩余字节为公钥。
            byte[] pubKeyBytes = new byte[buf.readableBytes()];
            buf.readBytes( pubKeyBytes );
            String publicKey = new String( pubKeyBytes, java.nio.charset.StandardCharsets.UTF_8 );
            //RSA加密密码。seed是随机字节(含>=0x80)，必须用ISO-8859-1做byte↔String的双射转换，
            //不能用US_ASCII（会把>=0x80的字节替换成0x3F）或UTF-8（多字节编码破坏原始字节）。
            String seedStr = new String( this.authSeed, java.nio.charset.StandardCharsets.ISO_8859_1 );
            byte[] encrypted = CachingSha2PasswordPlugin.encrypt(
                    "8.0.5",
                    publicKey, mysqlServerConfig.getPassword(), seedStr, "ISO-8859-1" );
            //发送加密后的密码（带包头）。
            ByteBuf encBuf = ctx.alloc().buffer( 4 + encrypted.length );
            encBuf.writeMediumLE( encrypted.length );
            encBuf.writeByte( packetId + 1 );
            encBuf.writeBytes( encrypted );
            ctx.writeAndFlush( encBuf );
        } catch (Throwable e) {
            log.error( "caching_sha2_password full-auth加密密码失败！err={}", e.toString(), e );
            sessionStatus = SESSION_CLOSED;
            trueClose();
        }
    }

    /**
     * 处理 SESSION_USING 阶段的命令响应包，按 MySQL 协议解析并回调 {@link #sessionCallback}。
     * <p>主分支（按首字节 status）：
     * <ul>
     *   <li>OK / EOF：若 resultStatus==RESULT_DATA 视为结果集结束（回调 receiveRowDataEOFPacket，
     *       根据 SERVER_MORE_RESULTS_EXISTS 决定是否解绑 callback）；否则回调 receiveOkPacket 并解绑；</li>
     *   <li>ERROR：解析错误号/文本，回调 receiveErrorPacket 并解绑，isSuccess 置 false；</li>
     *   <li>其它：进入结果集子状态机——RESULT_INIT 收 ResultSetHeaderPacket 并转入 RESULT_FIELD；
     *       RESULT_FIELD 收 field 包直到达到 fieldCount 后转入 RESULT_DATA；RESULT_DATA 收 row 数据包。</li>
     * </ul>
     * 累计 rxBytes 用于统计。
     *
     * @param ctx Netty 上下文
     * @param buf 后端命令响应包
     */
    private void handleCommandResponse(ChannelHandlerContext ctx, ByteBuf buf) {
        //标记接收字节数。
        rxBytes += buf.readableBytes();
        byte packetId = buf.getByte( 3 );
        byte status = buf.getByte( 4 );
        switch (status) {
            case MySqlPacket.PACKET_OK:
            case MySqlPacket.PACKET_EOF:
                if (resultStatus == RESULT_DATA) {
                    OkPacket okPacket = new OkPacket();
                    okPacket.readPayLoad( buf );
                    buf.resetReaderIndex();
                    sessionCallback.receiveRowDataEOFPacket( packetId, buf );
                    //确定没有更多数据了，再解绑，此处可能有问题！
                    if (!okPacket.hasStatusFlag( MySqlPacket.SERVER_MORE_RESULTS_EXISTS )) {
                        unbindCallback();
                    } else {
                        resultStatus = RESULT_INIT;
                    }
                } else {
                    sessionCallback.receiveOkPacket( packetId, buf );
                    //直接解绑吧。
                    unbindCallback();
                }
                break;
            case MySqlPacket.PACKET_ERROR:
                ErrorPacket errorPacket = new ErrorPacket();
                errorPacket.readPayLoad( buf );
                buf.resetReaderIndex();
                this.failMessage( errorPacket.errorNo, errorPacket.message );
                //直接转发走
                sessionCallback.receiveErrorPacket( packetId, buf );
                isSuccess = false;
                //都报错了，直接解绑
                unbindCallback();
                break;
            default:
                switch (resultStatus) {
                    case RESULT_INIT:
                        ResultSetHeaderPacket headerPacket = new ResultSetHeaderPacket();
                        headerPacket.readPayLoad( buf );
                        resultFieldCount = headerPacket.fieldCount;
                        buf.resetReaderIndex();
                        resultStatus = RESULT_FIELD;
                        sessionCallback.receiveResultSetHeaderPacket( packetId, buf );
                        break;
                    case RESULT_FIELD:
                        //field区
                        sessionCallback.receiveFieldDataPacket( packetId, buf );
                        resultFieldPos++;
                        if (resultFieldPos >= resultFieldCount) {
                            resultStatus = RESULT_DATA;
                        }
                        break;
                    default:
                        //数据区
                        sessionCallback.receiveRowDataPacket( packetId, buf );
                        dataRowsCount++;
                        break;
                }
        }
    }

    /**
     * 发出当前命令。仅当 {@link #sql} 非空时构造 {@link CommandPacket}（COM_QUERY）写出并 flush。
     * 由 {@link #addCommand}（SESSION_NORMAL 后）或 {@link #handleAuthResponse}（鉴权 Ok 后）触发。
     * 累计 txBytes 用于统计。
     */
    private void execute() {
        if (this.sql != null) {
            ByteBuf buf = channel.alloc().buffer();
            CommandPacket packet = new CommandPacket();
            packet.command = MySqlPacket.CMD_QUERY;
            packet.arg = sql;
            packet.writePayLoad( buf );
            if (log.isTraceEnabled()) {
                log.trace( "MySQL执行: {}", sql );
            }
            //标记发送字节数。
            txBytes += buf.readableBytes();
            channel.writeAndFlush( buf );
        }
    }

    /**
     * 根据 plugin 名 scramble 明文密码，供鉴权握手与 AuthSwitch 使用。
     * <ul>
     *   <li>caching_sha2_password -> {@link CachingSha2PasswordPlugin#scrambleCachingSha2}；</li>
     *   <li>其它（含 mysql_native_password）-> {@link MySqlNativePasswordPlugin#scramble411}。</li>
     * </ul>
     * 空密码返回 null。
     *
     * @param pass       明文密码
     * @param pluginName 鉴权 plugin 名
     * @param seed       auth_plugin_data（来自握手或 AuthSwitch）
     * @return scramble 后的字节数组，或 null（空密码）
     */
    private byte[] buildPassword(String pass, String pluginName, byte[] seed) {
        if (pass == null || pass.length() == 0) {
            return null;
        }

        // 根据plugin不同返回对应的加密数据。。
        if (CachingSha2PasswordPlugin.PROTOCOL_PLUGIN_NAME.equals( pluginName )) {
            return CachingSha2PasswordPlugin.scrambleCachingSha2( pass, seed );
        } else {
            return MySqlNativePasswordPlugin.scramble411( pass, seed );
        }
    }

    /**
     * 将后端握手包中的 auth_plugin_data_part_one + auth_plugin_data_part_two 拼成完整 seed。
     *
     * @param packet 后端握手包
     * @return 拼接后的 seed 字节数组
     */
    private byte[] buildAuthSeed(AuthHandshakeRequestPacket packet) {
        int sl1 = packet.authPluginDataPartOne.length;
        int sl2 = packet.authPluginDataPartTwo.length;
        byte[] seed = new byte[sl1 + sl2];
        System.arraycopy( packet.authPluginDataPartOne, 0, seed, 0, sl1 );
        System.arraycopy( packet.authPluginDataPartTwo, 0, seed, sl1, sl2 );
        return seed;
    }

    /**
     * 绑定前端回调，并刷新 {@link #lastRequestTime}。若当前处于 SESSION_NORMAL（闲置）则转入
     * SESSION_USING；若处于 SESSION_AUTH 则状态保持（等鉴权 Ok 后再 execute）。
     *
     * @param sessionCallback 前端回调
     */
    private void bindCallback(MySqlSessionCallback sessionCallback) {
        this.sessionCallback = sessionCallback;
        this.lastRequestTime = SystemClock.now();
        if (this.sessionStatus == SESSION_NORMAL) {
            this.sessionStatus = SESSION_USING;
        }
    }

    /**
     * 解绑当前回调，执行命令收尾：
     * <ol>
     *   <li>计算执行耗时并 {@link StatsManager#statsSql} 上报 SQL 统计（含 cluster/db/table/字节/行数）；</li>
     *   <li>把 database/table/sql/sqlType/计数器/结果集状态全部归零，准备复用；</li>
     *   <li>回调 {@link MySqlSessionCallback#onFinish()} 通知前端完成，置 null callback；</li>
     *   <li>sessionStatus 转回 SESSION_NORMAL；</li>
     *   <li>调用 {@link MySqlPool#release(Channel)} 把 channel 归还连接池。</li>
     * </ol>
     */
    private void unbindCallback() {
        long now = SystemClock.now();
        long exeMillis = (now - this.lastRequestTime);
        this.lastRequestTime = now;
        //最后统计执行信息。
        StatsManager.statsSql( this.sessionCallback.getClientInfo(), this.mysqlServerConfig.getClusterId(), this.mysqlServerConfig.getId(), database, table, sql, sqlType,
                isSuccess, Math.max( dataRowsCount, affectRowsCount ), txBytes, rxBytes, exeMillis, now );
        //数据归零
        this.database = null;
        this.table = null;
        this.sql = null;
        this.sqlType = SQLType.OTHER.getValue();
        this.isSuccess = true;
        this.dataRowsCount = 0;
        this.affectRowsCount = 0;
        this.rxBytes = 0;
        this.txBytes = 0;
        this.resultStatus = RESULT_INIT;
        this.resultFieldCount = 0;
        this.resultFieldPos = 0;
        //解绑callback。
        if (this.sessionCallback != null) {
            this.sessionCallback.onFinish();
            this.sessionCallback = null;
        }
        //设置状态。
        this.sessionStatus = SESSION_NORMAL;
        //释放channelPool。
        if (channelPool != null) {
            channelPool.release( channel );
        }
    }

}
