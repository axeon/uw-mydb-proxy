package uw.mydb.proxy;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.constant.GlobalConstants;
import uw.mydb.mysql.MySqlClient;
import uw.mydb.mysql.MySqlSession;
import uw.mydb.mysql.MySqlSessionCallback;
import uw.mydb.protocol.packet.*;
import uw.mydb.protocol.constant.MySQLCapability;
import uw.mydb.protocol.constant.MySqlErrorCode;
import uw.mydb.sqlparser.SqlParseResult;
import uw.mydb.sqlparser.SqlParser;
import uw.mydb.stats.StatsManager;
import uw.mydb.util.CachingSha2PasswordPlugin;
import uw.mydb.util.MySqlNativePasswordPlugin;
import uw.mydb.util.RandomUtils;
import uw.mydb.util.SystemClock;
import uw.mydb.vo.MydbProxyConfig;

import java.util.Arrays;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 代理服务器的会话实体。
 *
 * @author axeon
 */
public class ProxySession implements MySqlSessionCallback {

    private static final Logger logger = LoggerFactory.getLogger( ProxySession.class );

    /**
     * 多节点执行的异步线程池。
     */
    private static final ThreadPoolExecutor multiNodeExecutor = new ThreadPoolExecutor( 5, 500, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setDaemon( true ).setNameFormat( "multi_node_service-%d" ).build(), new ThreadPoolExecutor.CallerRunsPolicy() );

    /**
     * 全局统一的sessionId生成器。
     */
    private static AtomicLong sessionIdGenerator = new AtomicLong();

    /**
     * 默认验证插件。
     */
    public String clientAuthPluginName = MySqlNativePasswordPlugin.PROTOCOL_PLUGIN_NAME;

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
    private long txBytes;

    /**
     * 接收字节数。
     */
    private long rxBytes;

    /**
     * 是否是只读sql
     */
    private boolean isMasterSql = false;

    /**
     * 是否执行失败了
     */
    private boolean isExeSuccess = true;

    /**
     * 是否已登录。
     */
    private boolean isLogon;

    /**
     * 绑定的channel
     */
    private ChannelHandlerContext ctx;

    /**
     * session Id
     */
    private long id;

    /**
     * 用户名
     */
    private String user;

    /**
     * 连接的主机。
     */
    private String host;

    /**
     * 连接的端口。
     */
    private int port;

    /**
     * 连接的database。
     */
    private String database;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 字符集索引
     */
    private int charsetIndex;

    /**
     * auth验证的seed
     */
    private byte[] authSeed;

    /**
     * 最后反馈时间
     */
    private long lastResponseTime;

    /**
     * 查询开始时间
     */
    private long queryStartTime;

    /**
     * sql解析结果。
     */
    private SqlParseResult routeResult;


    public ProxySession(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.id = sessionIdGenerator.incrementAndGet();
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isLogon() {
        return isLogon;
    }

    /**
     * 获得当前数据库。
     *
     * @return
     */
    public String getDatabase() {
        return database;
    }

    /**
     * 设置database。
     */
    public void setDatabase(String database) {
        if (StringUtils.isNotBlank( database )) {
            this.database = database;
            MySqlSession mySqlSession = MySqlClient.getMySqlSession( MydbConfigService.getProxyConfig().getBaseCluster(), true );
            mySqlSession.addCommand( this, CommandPacket.build( "use " + this.database ), true );
        } else {
            //报错，找不到这个schema。
            onFailMessage( MySqlErrorCode.ER_NO_DB_ERROR, "No database!" );
        }
    }

    /**
     * 发送握手包。
     *
     * @param ctx
     */
    public void sendHandshake(ChannelHandlerContext ctx) {
        // 生成认证数据
        byte[] rand1 = RandomUtils.randomBytes( 8 );
        byte[] rand2 = RandomUtils.randomBytes( 12 );
        // 保存认证数据
        byte[] seed = new byte[rand1.length + rand2.length];
        System.arraycopy( rand1, 0, seed, 0, rand1.length );
        System.arraycopy( rand2, 0, seed, rand1.length, rand2.length );
        this.authSeed = seed;
        // 发送握手数据包
        AuthHandshakeRequestPacket hs = new AuthHandshakeRequestPacket();
        hs.packetId = 0;
        hs.protocolVersion = GlobalConstants.PROTOCOL_VERSION;
        hs.serverVersion = GlobalConstants.SERVER_VERSION;
        hs.connectionId = id;
        hs.authPluginDataPartOne = rand1;
        hs.authPluginDataPartTwo = rand2;
        hs.serverCapabilities = MySQLCapability.getServerCapabilities();
        hs.authPluginName = MySqlNativePasswordPlugin.PROTOCOL_PLUGIN_NAME;
        //写channel里
        hs.writeToChannel( ctx );
        ctx.flush();
    }

    /**
     * 处理验证包。
     *
     * @param ctx
     * @param buf
     */
    public void auth(ChannelHandlerContext ctx, ByteBuf buf) {
        MydbProxyConfig config = MydbConfigService.getProxyConfig();
        AuthHandshakeResponsePacket authPacket = new AuthHandshakeResponsePacket();
        authPacket.readPayLoad( buf );
        String authPluginName = authPacket.authPluginName;
        long capabilities = authPacket.clientCapability;

        //如果客户端开启压缩，那么直接返回不支持。
        if (MySQLCapability.isClientCompress( capabilities )) {
            onFailMessage( ctx, MySqlErrorCode.ER_NET_UNCOMPRESS_ERROR, "Can not use compression protocol!" );
            onFinish();
            ctx.close();
            return;
        }

        //检测是否需要做验证切换，这个场景几乎不会出现。。。。
//        if (!isChangeAuthPlugin && MySQLCapability.isPluginAuth(capabilities) && StringUtils.isNotEmpty(authPluginName)&&!authPluginName.equals(clientAuthPluginName)) {
//            isChangeAuthPlugin = true;
//            AuthSwitchRequestPacket authSwitchRequestPacket = new AuthSwitchRequestPacket();
//            authSwitchRequestPacket.authPluginData = authSeed;
//            authSwitchRequestPacket.authPluginName = StringUtils.isEmpty(authPluginName) ? MysqlNativePasswordPlugin.PROTOCOL_PLUGIN_NAME : authPluginName;
//            authSwitchRequestPacket.writeToChannel(ctx);
//            return;
//        }

        if (!StringUtils.equals( config.getUsername(), authPacket.username )) {
            onFailMessage( ctx, MySqlErrorCode.ER_ACCESS_DENIED_ERROR, "Access denied for user '" + authPacket.username + "', because user is not exists! " );
            onFinish();
            ctx.close();
            return;
        }

        // 检查密码匹配。
        byte[] encryptPass = null;
        if (authPluginName.equals( CachingSha2PasswordPlugin.PROTOCOL_PLUGIN_NAME )) {
            encryptPass = CachingSha2PasswordPlugin.scrambleCachingSha2( config.getPassword(), this.authSeed );
        } else {
            encryptPass = MySqlNativePasswordPlugin.scramble411( config.getPassword(), this.authSeed );
        }

        if (!Arrays.equals( encryptPass, authPacket.password )) {
            onFailMessage( ctx, MySqlErrorCode.ER_PASSWORD_NO_MATCH, "Access denied for user '" + authPacket.username + "', because password is error " );
            onFinish();
            ctx.close();
            return;
        }

        // 检查scheme权限
        if (StringUtils.isNotBlank( authPacket.database )) {
            this.database = authPacket.database;
        }
        // 设置字符集编码
        this.charsetIndex = (authPacket.charsetIndex & 0xff);
        //设置session用户
        this.user = authPacket.username;
        this.isLogon = true;
        this.authSeed = null;
        OkPacket.writeAuthOkToChannel( ctx );
    }

    /**
     * 收到Ok数据包。
     *
     * @param buf
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        OkPacket okPacket = new OkPacket();
        okPacket.readPayLoad( buf );
        affectRowsCount += okPacket.affectedRows;
        buf.resetReaderIndex();
        ctx.write( buf.retain() );
    }

    /**
     * 收到Error数据包。
     *
     * @param buf
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write( buf.retain() );
        isExeSuccess = false;
    }

    /**
     * 收到ResultSetHeader数据包。
     *
     * @param buf
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write( buf.retain() );
    }

    /**
     * 收到FieldPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write( buf.retain() );
    }

    /**
     * 收到FieldEOFPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write( buf.retain() );
    }

    /**
     * 收到RowDataPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        dataRowsCount++;
        ctx.write( buf.retain() );
    }

    /**
     * 收到RowDataEOFPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write( buf.retain() );
    }

    /**
     * 错误提示。
     *
     * @param errorNo
     * @param info
     */
    @Override
    public void onFailMessage(int errorNo, String info) {
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.packetId = 1;
        errorPacket.errorNo = errorNo;
        errorPacket.message = info;
        errorPacket.writeToChannel( ctx );
        this.ctx.flush();
    }

    /**
     * 卸载。
     */
    @Override
    public void onFinish() {
        //开始统计数据了。
        this.exeTime = SystemClock.now() - queryStartTime;
        if (routeResult != null) {
            String statsTable = routeResult.getTable();
            String statsSql = routeResult.getSourceSql();
            int statsRouteSize = (routeResult.isSingle() ? 1 : routeResult.getSqlInfoList().size());
            //开始统计。
            StatsManager.statsMydb( host, database, statsTable, isMasterSql, isExeSuccess, exeTime, dataRowsCount, affectRowsCount, txBytes, rxBytes );
            StatsManager.statsSlowSql( host, database, statsSql, statsRouteSize, Math.max( dataRowsCount, affectRowsCount ), txBytes, rxBytes, exeTime, queryStartTime );
        }
        //数据归零
        routeResult = null;
        isMasterSql = false;
        isExeSuccess = true;
        this.exeTime = 0;
        this.dataRowsCount = 0;
        this.affectRowsCount = 0;
        this.rxBytes = 0;
        this.txBytes = 0;
        //最后才能flush，否则会出问题！！！
        this.ctx.flush();
    }

    /**
     * 切换数据库操作。
     *
     * @param ctx
     * @param buf
     */
    public void initDB(ChannelHandlerContext ctx, ByteBuf buf) {
        CommandPacket cmd = new CommandPacket();
        cmd.readPayLoad( buf );
        //切换Schema
        this.setDatabase( new String( cmd.arg ) );
    }

    /**
     * handler 查询语句。
     *
     * @param ctx
     * @param buf
     */
    public void query(ChannelHandlerContext ctx, ByteBuf buf) {
        rxBytes += buf.readableBytes();
        queryStartTime = SystemClock.now();
        //如果schema没有任何表分区定义，则直接转发到默认库。
        //读取sql
        CommandPacket cmd = new CommandPacket();
        cmd.readPayLoad( buf );
        String sql = new String( cmd.arg );
        if (logger.isTraceEnabled()) {
            logger.trace( "接收到SQL: {}", sql );
        }
        //根据解析结果判定，当前支持1.单实例执行；2.多实例执行
        SqlParser parser = new SqlParser( this, sql );
        routeResult = parser.parse();
        //sql解析后，routeResult=null的，可能已经在parser里处理过了。
        if (routeResult.hasError()) {
            //error code>0的，发送错误信息。
            if (routeResult.getErrorCode() > 0) {
                onFailMessage( ctx, routeResult.getErrorCode(), routeResult.getErrorMessage() );
                onFinish();
            }
            return;
        }
        //压测时，可直接返回ok包的。
        if (routeResult.isSingle()) {
            //单实例执行直接绑定执行即可。
            MySqlSession mySqlSession = MySqlClient.getMySqlSession( routeResult.getSqlInfo().getClusterId(), routeResult.isMaster() );
            if (mySqlSession == null) {
                onFailMessage( ctx, MySqlErrorCode.ERR_NO_ROUTE_NODE, "Can't route to mysqlCluster!" );
                onFinish();
                logger.warn( "无法找到合适的mysqlSession!" );
                return;
            }
            mySqlSession.addCommand(this , routeResult.getSqlInfo(), routeResult.isMaster() );
        } else {
            //多实例执行使用CountDownLatch同步返回所有结果后，再执行转发，可能会导致阻塞。
            multiNodeExecutor.submit( new ProxyMultiNodeHandler( this.ctx, routeResult ) );
        }
    }

    /**
     * ping操作。
     *
     * @param ctx
     */
    public void ping(ChannelHandlerContext ctx) {
        OkPacket.writeOkToChannel( ctx );
    }

    /**
     * 前端关闭操作。
     *
     * @param ctx
     */
    public void close(ChannelHandlerContext ctx) {
        ctx.close();
    }

    /**
     * kill操作。
     *
     * @param ctx
     * @param buf
     */
    public void kill(ChannelHandlerContext ctx, ByteBuf buf) {
        onFailMessage( ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT OPT" );
        onFinish();
    }

    /**
     * pstmt预编译。
     *
     * @param ctx
     * @param buf
     */
    public void stmtPrepare(ChannelHandlerContext ctx, ByteBuf buf) {
        onFailMessage( ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT OPT" );
        onFinish();
    }

    /**
     * pstmt执行。
     *
     * @param ctx
     * @param buf
     */
    public void stmtExecute(ChannelHandlerContext ctx, ByteBuf buf) {
        onFailMessage( ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT OPT" );
        onFinish();
    }

    /**
     * pstmt执行关闭。
     *
     * @param ctx
     * @param buf
     */
    public void stmtClose(ChannelHandlerContext ctx, ByteBuf buf) {
        onFailMessage( ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT OPT" );
        onFinish();
    }

    /**
     * 心跳操作。
     *
     * @param ctx
     * @param buf
     */
    public void heartbeat(ChannelHandlerContext ctx, ByteBuf buf) {
        OkPacket.writeOkToChannel( ctx );
        onFinish();
    }

    /**
     * 错误提示。
     *
     * @param ctx
     * @param errorNo
     * @param info
     */
    public void onFailMessage(ChannelHandlerContext ctx, int errorNo, String info) {
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.packetId = 1;
        errorPacket.errorNo = errorNo;
        errorPacket.message = info;
        errorPacket.writeToChannel( ctx );
        ctx.flush();
    }

    /**
     * 更新最后访问时间。
     */
    public void updateLastResponseTime() {
        this.lastResponseTime = SystemClock.now();
    }
}
