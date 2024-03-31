package uw.mydb.mysql;


import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import uw.mydb.protocol.packet.*;
import uw.mydb.protocol.util.MySQLCapability;
import uw.mydb.sqlparser.SqlParseResult;
import uw.mydb.util.CachingSha2PasswordPlugin;
import uw.mydb.util.MySqlNativePasswordPlugin;
import uw.mydb.util.SystemClock;
import uw.mydb.vo.MysqlServerConfig;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Mysql的会话实例。
 *
 * @author axeon
 */
public class MySqlSession {

    /**
     * 删除状态。
     */
    public static final int STATE_REMOVED = -2;

    /**
     * 标记删除状态
     */
    public static final int STATE_RESERVED = -1;

    /**
     * 初始状态，此状态不可用
     */
    public static final int STATE_INIT = 0;

    /**
     * 验证中状态，此状态不可用。
     */
    public static final int STATE_AUTH = 1;
    /**
     * 正常状态。
     */
    public static final int STATE_NORMAL = 2;

    /**
     * 使用中。。。
     */
    public static final int STATE_USING = 3;

    /**
     * 结果集中间状态
     */
    public static final int RESULT_FIELD = 2;
    /**
     * 结果集初始状态
     */
    public static final int RESULT_INIT = 0;
    /**
     * 结果集开始状态
     */
    public static final int RESULT_START = 1;

    /**
     * logger
     */
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( MySqlSession.class );

    /**
     * 并发状态更新。
     */
    private static final AtomicIntegerFieldUpdater<MySqlSession> STATE_UPDATER;

    static {
        STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater( MySqlSession.class, "state" );
    }

    /**
     * 创建时间.
     */
    final long createTime = SystemClock.now();
    /**
     * 开始使用时间.
     */
    long lastAccess = createTime;
    /**
     * 前端对应的ctx。
     */
    MySqlSessionCallback sessionCallback;
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
    private volatile int state = STATE_INIT;

    /**
     * 结果集状态。
     * 0 正常 1 包头状态 2 field状态 3.row data状态
     */
    private int resultStatus = RESULT_INIT;

    /**
     * 数据行计数。
     */
    private int dataRowsCount;

    /**
     * 受影响行计数。
     */
    private int affectRowsCount;

    /**
     * 执行消耗时间。
     */
    private long exeTime;

    /**
     * 发送字节数。
     */
    private long sendBytes;

    /**
     * 接收字节数。
     */
    private long recvBytes;

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


    public MySqlSession(MysqlServerConfig mysqlServerConfig, Channel channel) {
        this.mysqlServerConfig = mysqlServerConfig;
        this.channel = channel;
    }

    /**
     * 异步执行一条sql。
     *
     * @param command
     */
    public void exeCommand(boolean isMasterSql, CommandPacket command) {
        this.isMasterSql = isMasterSql;
        ByteBuf buf = channel.alloc().buffer();
        this.command = command;
        this.command.writePayLoad( buf );
        //标记发送字节数。
        sendBytes += buf.readableBytes();
        channel.writeAndFlush( buf );
    }

    /**
     * 异步执行一条sql。
     *
     * @param sqlInfo
     */
    public void exeCommand(boolean isMasterSql, SqlParseResult.SqlInfo sqlInfo) {
        this.isMasterSql = isMasterSql;
        this.database = sqlInfo.getDatabase();
        this.table = sqlInfo.getTable();
        ByteBuf buf = channel.alloc().buffer();
        this.command = sqlInfo.genPacket();
        this.command.writePayLoad( buf );
        //标记发送字节数。
        sendBytes += buf.readableBytes();
        channel.writeAndFlush( buf );
    }

    /**
     * 绑定到前端session。
     *
     * @param sessionCallback
     */
    public void bind(MySqlSessionCallback sessionCallback) {
        this.sessionCallback = sessionCallback;
        this.lastAccess = SystemClock.now();
    }

    /**
     * 解绑。
     */
    public void unbind() {
        long now = SystemClock.now();
        exeTime = (now - this.lastAccess);
        this.lastAccess = now;

        //最后统计mysql执行信息。
//        StatsManager.statsMysql( mysqlService.getGroupName(), mysqlService.getName(), database, isMasterSql, isExeSuccess, exeTime, dataRowsCount, affectRowsCount, sendBytes,
//                recvBytes );

        if (this.sessionCallback != null) {
            //再执行解绑
            this.sessionCallback.onFinish();
            this.sessionCallback = null;
        }
        //数据归零
        command = null;
        isMasterSql = false;
        isExeSuccess = true;
        this.exeTime = 0;
        this.dataRowsCount = 0;
        this.affectRowsCount = 0;
        this.recvBytes = 0;
        this.sendBytes = 0;

    }

    /**
     * 处理验证返回结果。
     *
     * @param buf
     */
    protected void handleAuthResponse(ChannelHandlerContext ctx, ByteBuf buf) {
        byte status = buf.getByte( 4 );
        switch (status) {
            case MySqlPacket.PACKET_AUTH_SWITCH:
                logger.info( "PACKET_AUTH_SWITCH" );
                AuthSwitchRequestPacket authSwitchRequestPacket = new AuthSwitchRequestPacket();
                authSwitchRequestPacket.readPayLoad( buf );
                AuthSwitchResponsePacket authSwitchResponsePacket = new AuthSwitchResponsePacket();
                authSwitchResponsePacket.packetId = ++authSwitchRequestPacket.packetId;
                authSwitchResponsePacket.data = buildPassword( mysqlServerConfig.getPass(), authSwitchRequestPacket.authPluginName, authSwitchRequestPacket.authPluginData );
                authSwitchResponsePacket.writeToChannel( ctx );
                ctx.flush();
                break;
            case MySqlPacket.PACKET_AUTH_MORE_DATA:
                logger.info( "PACKET_AUTH_MORE_DATA" );
                AuthMoreDataPacket packet = new AuthMoreDataPacket();
                packet.readPayLoad( buf );
//                AuthSwitchResponsePacket authSwitchResponsePacket2 = new AuthSwitchResponsePacket();
//                authSwitchResponsePacket2.packetId =2;
//                authSwitchResponsePacket2.writeToChannel( ctx );
//                ctx.flush();
                break;
            case MySqlPacket.PACKET_OK:
                logger.info( "PACKET_OK" );
                OKPacket okPacket = new OKPacket();
                okPacket.readPayLoad( buf );
                setState( STATE_NORMAL );
                break;
            case MySqlPacket.PACKET_ERROR:
                logger.info( "PACKET_ERROR" );
                //报错了，直接关闭吧。
                ErrorPacket errorPacket = new ErrorPacket();
                errorPacket.readPayLoad( buf );
                logger.error( "MySQL[{}]服务器验证阶段报错{}:{}", mysqlServerConfig.toString(), errorPacket.errorNo, errorPacket.message );
                setState( STATE_REMOVED );
                trueClose();
                break;
            default:
                logger.warn( "收到未知的登录数据包！status={}",status );
        }
    }

    /**
     * 处理握手流程。
     *
     * @param buf
     */
    protected void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
        byte status = buf.getByte( 4 );
        if (status == MySqlPacket.PACKET_ERROR) {
            ErrorPacket errorPacket = new ErrorPacket();
            errorPacket.readPayLoad( buf );
            logger.error( "MySQL[{}]服务器握手阶段报错{}:{}", mysqlServerConfig.toString(), errorPacket.errorNo, errorPacket.message );
            //报错了，直接关闭吧。
            setState( STATE_REMOVED );
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
        handshakeResponsePacket.username = mysqlServerConfig.getUser();
        handshakeResponsePacket.authPluginName = StringUtils.isNotBlank( handshakePacket.authPluginName ) ? handshakePacket.authPluginName :
                MySqlNativePasswordPlugin.PROTOCOL_PLUGIN_NAME;
        handshakeResponsePacket.password = buildPassword( mysqlServerConfig.getPass(), handshakeResponsePacket.authPluginName, buildAuthSeed( handshakePacket ) );
        handshakeResponsePacket.writeToChannel( ctx );
        ctx.flush();
        //进入验证模式。
        setState( STATE_AUTH );
    }

    /**
     * 处理命令返回结果。
     *
     * @param buf
     */
    protected void handleCommandResponse(ChannelHandlerContext ctx, ByteBuf buf) {
        //标记接收字节数。
        recvBytes += buf.readableBytes();
        byte packetId = buf.getByte( 3 );
        byte status = buf.getByte( 4 );
        switch (status) {
            case MySqlPacket.PACKET_OK:
                sessionCallback.receiveOkPacket( packetId, buf );
                //收到数据就可以解绑了。
                unbind();
                break;
            case MySqlPacket.PACKET_ERROR:
                //直接转发走
                sessionCallback.receiveErrorPacket( packetId, buf );
                isExeSuccess = false;
                //都报错了，直接解绑
                unbind();
                break;
            case MySqlPacket.PACKET_EOF:
                //包长度小于9才可能是EOF，否则可能是数据包。
                if (buf.readableBytes() <= 9) {
                    //此时要判断resultStatus，确定结束才可以解绑
                    if (checkResultEnd()) {
                        EOFPacket eof = new EOFPacket();
                        eof.readPayLoad( buf );
                        //之前读过了，必须要重置一下。
                        buf.resetReaderIndex();
                        sessionCallback.receiveRowDataEOFPacket( packetId, buf );
                        //确定没有更多数据了，再解绑，此处可能有问题！
                        if (!eof.hasStatusFlag( MySqlPacket.SERVER_MORE_RESULTS_EXISTS )) {
                            unbind();
                        } else {
                            resultStatus = RESULT_INIT;
                        }
                    } else {
                        sessionCallback.receiveFieldDataEOFPacket( packetId, buf );
                    }
                    break;
                }
            default:
                switch (resultStatus) {
                    case RESULT_INIT:
                        //此时是ResultSetHeader
                        setResultStart();
                        sessionCallback.receiveResultSetHeaderPacket( packetId, buf );
                        break;
                    case RESULT_START:
                        //field区
                        sessionCallback.receiveFieldDataPacket( packetId, buf );
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
     * 错误提示。
     *
     * @param errorNo
     * @param info
     */
    protected void failMessage(int errorNo, String info) {
        if (sessionCallback != null) {
            sessionCallback.onFailMessage( errorNo, info );
        }
    }

    /**
     * 真正关闭连接。
     */
    protected void trueClose() {
        this.channel.close();
    }

    protected boolean compareAndSet(int expectState, int newState) {
        return STATE_UPDATER.compareAndSet( this, expectState, newState );
    }

    protected int getState() {
        return STATE_UPDATER.get( this );
    }

    protected void setState(int newState) {
        STATE_UPDATER.set( this, newState );
    }

    @Override
    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString( hashCode() ) + ":" + getState();
    }

    /**
     * 生成密码数据。
     *
     * @param pass
     * @param seed
     * @return
     * @throws NoSuchAlgorithmException
     */
    private static byte[] buildPassword(String pass, String pluginName, byte[] seed) {
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
    private static byte[] buildAuthSeed(AuthHandshakeRequestPacket packet) {
        int sl1 = packet.authPluginDataPartOne.length;
        int sl2 = packet.authPluginDataPartTwo.length;
        byte[] seed = new byte[sl1 + sl2];
        System.arraycopy( packet.authPluginDataPartOne, 0, seed, 0, sl1 );
        System.arraycopy( packet.authPluginDataPartTwo, 0, seed, sl1, sl2 );
        return seed;
    }

    /**
     * 设置结果集开始。
     */
    private void setResultStart() {
        resultStatus = RESULT_START;
    }

    /**
     * 检查结果集状态。
     */
    private boolean checkResultEnd() {
        if (resultStatus == RESULT_START) {
            resultStatus = RESULT_FIELD;
            return false;
        } else {
            resultStatus = RESULT_INIT;
            return true;
        }
    }
}
