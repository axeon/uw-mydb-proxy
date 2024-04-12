package uw.mydb.mysql;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import uw.mydb.protocol.constant.MySQLCapability;
import uw.mydb.protocol.packet.*;
import uw.mydb.sqlparse.SqlParseResult;
import uw.mydb.util.CachingSha2PasswordPlugin;
import uw.mydb.util.MySqlNativePasswordPlugin;
import uw.mydb.util.SystemClock;
import uw.mydb.vo.MysqlServerConfig;

import java.security.NoSuchAlgorithmException;

/**
 * Mysql的会话实例。
 *
 * @author axeon
 */
public class MySqlSession {

    /**
     * logger
     */
    private static final org.slf4j.Logger log = LoggerFactory.getLogger( MySqlSession.class );

    /**
     * 删除状态。
     */
    private static final int SESSION_CLOSED = -1;

    /**
     * 初始状态，此状态不可用。
     */
    private static final int SESSION_INIT = 0;

    /**
     * 验证中状态，此状态不可用。
     */
    private static final int SESSION_AUTH = 1;

    /**
     * 正常状态。
     */
    private static final int SESSION_NORMAL = 2;

    /**
     * 使用中状态。
     */
    private static final int SESSION_USING = 3;

    /**
     * 结果集初始状态。
     */
    private static final int RESULT_INIT = 0;

    /**
     * 结果集列状态。
     */
    private static final int RESULT_FIELD = 1;

    /**
     * 结果集数据状态。
     */
    private static final int RESULT_DATA = 2;

    /**
     * 创建时间.
     */
    private final long createTime = SystemClock.now();

    /**
     * 开始使用时间.
     */
    private long lastQueryTime = createTime;

    /**
     * 回调对象。
     */
    private MySqlSessionCallback sessionCallback;

    /**
     * channel pool。
     */
    private MySqlPool channelPool = null;

    /**
     * 对应的channel。
     */
    private Channel channel;

    /**
     * mysql服务器配置。
     */
    private MysqlServerConfig mysqlServerConfig;

    /**
     * 连接状态。
     */
    private volatile int sessionStatus = SESSION_INIT;

    /**
     * 结果集状态。
     * 0 正常 1 包头状态 2 field状态 3.row data状态
     */
    private volatile int resultStatus = RESULT_INIT;

    /**
     * 数据行计数。
     */
    private int dataRowsCount;

    /**
     * 受影响行计数。
     */
    private int affectRowsCount;

    /**
     * 发送字节数。
     */
    private long txBytes;

    /**
     * 接收字节数。
     */
    private long rxBytes;

    /**
     * 执行sql所在的数据库
     */
    private String database;

    /**
     * 执行sql所在的表
     */
    private String table;

    /**
     * 是否是只读sql
     */
    private boolean isMasterSql = false;

    /**
     * 是否执行失败了
     */
    private boolean isExeSuccess = true;

    /**
     * 正在执行的指令。
     */
    private CommandPacket command;

    /**
     * result set fieldCount.
     */
    private int resultFieldCount = 0;

    /**
     * fieldPos。
     */
    private int resultFieldPos = 0;

    protected MySqlSession(MysqlServerConfig mysqlServerConfig, Channel channel) {
        this.mysqlServerConfig = mysqlServerConfig;
        this.channel = channel;
    }

    /**
     * 异步执行一条sql。
     *
     * @param sessionCallback
     * @param command
     */
    public void addCommand(MySqlSessionCallback sessionCallback, CommandPacket command, boolean isMasterSql) {
        bindCallback( sessionCallback );
        this.isMasterSql = isMasterSql;
        this.command = command;
        if (sessionStatus > SESSION_NORMAL) {
            executeCommand();
        }
    }

    /**
     * 异步执行一条sql。
     *
     * @param sessionCallback
     * @param sqlInfo
     */
    public void addCommand(MySqlSessionCallback sessionCallback, SqlParseResult.SqlInfo sqlInfo, boolean isMasterSql) {
        bindCallback( sessionCallback );
        this.isMasterSql = isMasterSql;
        this.database = sqlInfo.getDatabase();
        this.table = sqlInfo.getTable();
        this.command = sqlInfo.genPacket();
        if (sessionStatus > SESSION_NORMAL) {
            executeCommand();
        }
    }

    /**
     * 错误提示。
     *
     * @param errorNo
     * @param info
     */
    public void failMessage(int errorNo, String info) {
        if (sessionCallback != null) {
            sessionCallback.onFailMessage( errorNo, info );
        }
    }

    /**
     * 获得创建时间。
     *
     * @return
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 获得最后访问时间。
     *
     * @return
     */
    public long getLastQueryTime() {
        return lastQueryTime;
    }

    /**
     * 绑定channelPool。
     *
     * @param channelPool
     */
    protected void bindChannelPool(MySqlPool channelPool) {
        this.channelPool = channelPool;
    }

    /**
     * 处理命令返回结果。
     *
     * @param buf
     */
    protected void handleResponse(ChannelHandlerContext ctx, ByteBuf buf) {
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
     * 真正关闭连接。
     */
    protected void trueClose() {
        sessionStatus = SESSION_CLOSED;
        this.channel.close();
        try {
            this.channelPool.release( this.channel );
        } catch (Throwable e) {
            log.error( e.getMessage(), e );
        }
    }

    /**
     * 处理握手流程。
     *
     * @param buf
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
        handshakeResponsePacket.password = buildPassword( mysqlServerConfig.getPassword(), handshakeResponsePacket.authPluginName, buildAuthSeed( handshakePacket ) );
        handshakeResponsePacket.writeToChannel( ctx );
        ctx.flush();
        //进入验证模式。
        sessionStatus = SESSION_AUTH;
    }

    /**
     * 处理验证返回结果。
     *
     * @param buf
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
                //data 3:成功，2:请求public key，4:请求完整验证。
                if (authMoreDataPacket.data != 0x03) {
                    //快速返回失败信息。
                    authMoreDataPacket.packetId++;
                    authMoreDataPacket.writeToChannel( ctx );
                    ctx.flush();
                }
                break;
            case MySqlPacket.PACKET_OK:
                OkPacket okPacket = new OkPacket();
                okPacket.readPayLoad( buf );
                sessionStatus = SESSION_USING;
                executeCommand();
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
     * 处理命令返回结果。
     *
     * @param buf
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
                //直接转发走
                sessionCallback.receiveErrorPacket( packetId, buf );
                isExeSuccess = false;
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
     * 执行指令。
     */
    private void executeCommand() {
        if (this.command != null) {
            ByteBuf buf = channel.alloc().buffer();
            this.command.writePayLoad( buf );
            //标记发送字节数。
            txBytes += buf.readableBytes();
            channel.writeAndFlush( buf );
        }
    }

    /**
     * 生成密码数据。
     *
     * @param pass
     * @param seed
     * @return
     * @throws NoSuchAlgorithmException
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
     * 构造密码seed。
     *
     * @param packet
     * @return
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
     * 绑定到前端session。
     *
     * @param sessionCallback
     */
    private void bindCallback(MySqlSessionCallback sessionCallback) {
        this.sessionCallback = sessionCallback;
        this.lastQueryTime = SystemClock.now();
        if (this.sessionStatus == SESSION_NORMAL) {
            this.sessionStatus = SESSION_USING;
        }
    }

    /**
     * 解绑。
     */
    private void unbindCallback() {
        long now = SystemClock.now();
        long exeMillis = (now - this.lastQueryTime);
        this.lastQueryTime = now;
        //最后统计执行信息。
//        StatsManager.stats( 1, 1, database, table, isMasterSql, isExeSuccess, exeMillis, dataRowsCount, affectRowsCount, txBytes, rxBytes );
        //数据归零
        this.command = null;
        this.isMasterSql = false;
        this.isExeSuccess = true;
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
