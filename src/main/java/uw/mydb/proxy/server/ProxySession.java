package uw.mydb.proxy.server;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.common.util.SystemClock;
import uw.mydb.common.conf.MydbProxyConfig;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.constant.GlobalConstants;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.mysql.MySqlSession;
import uw.mydb.proxy.mysql.MySqlSessionCallback;
import uw.mydb.proxy.protocol.constant.MySQLCapability;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.protocol.packet.*;
import uw.mydb.proxy.sqlparse.SqlParseResult;
import uw.mydb.proxy.sqlparse.SqlParser;
import uw.mydb.proxy.stats.StatsManager;
import uw.mydb.proxy.util.CachingSha2PasswordPlugin;
import uw.mydb.proxy.util.MySqlNativePasswordPlugin;
import uw.mydb.proxy.util.RandomUtils;

import java.security.MessageDigest;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 前端代理会话实体，对应一条客户端到 proxy 的 MySQL 协议连接。
 * <p>
 * 角色与生命周期：
 * <ul>
 *   <li>由 {@link ProxyDataHandler#channelActive} 在连接建立时创建，绑定到 channel 的 {@code AttributeKey<ProxySession>} 上。</li>
 *   <li>实现 {@link MySqlSessionCallback}，作为后端 {@code MySqlSession} 执行结果回写到前端 channel 的回调入口。
 *       后端线程在工作线程中调用 {@link #receiveOkPacket}/{@link #receiveErrorPacket}/{@link #receiveResultSetHeaderPacket} 等方法，
 *       这些方法通过 {@code ctx.write()} 写入前端 channel（Netty 线程安全）。</li>
 *   <li>连接断开时由 {@link ProxyDataHandler#channelInactive} 从 {@link ProxySessionManager} 移除。</li>
 * </ul>
 * <p>
 * 状态机（{@link #isLogon}）：
 * <ol>
 *   <li>false：尚未通过认证，此时收到的包由 {@link #auth} 处理握手；认证成功置为 true。</li>
 *   <li>true：已登录，根据 MySQL 命令类型分发到 {@link #query}/{@link #ping}/{@link #initDB}/{@link #close}/{@link #kill}/{@link #stmtPrepare}/{@link #stmtExecute}/{@link #stmtClose}/{@link #heartbeat}。</li>
 * </ol>
 * <p>
 * 异步执行模型：所有可能阻塞的后端操作（获取 {@code MySqlSession}、下发命令、多节点聚合）均提交到 {@link #multiNodeExecutor} 异步执行，
 * 严禁在 Netty EventLoop 线程中阻塞等待，否则会拖垮整个 worker 线程组。线程池满时捕获 {@code RejectedExecutionException} 回写错误给客户端。
 * <p>
 * 单次命令的中间状态（{@link #sqlParseResult}/{@link #sqlInfo}/{@link #dataRowsCount}/{@link #affectRowsCount}/{@link #txBytes}/{@link #rxBytes}/{@link #isExeSuccess}）
 * 在命令开始时初始化，{@link #onFinish()} 时统一结算统计并归零，为下一条命令复用同一会话做准备。
 *
 * @author axeon
 */
public class ProxySession implements MySqlSessionCallback {

    private static final Logger logger = LoggerFactory.getLogger(ProxySession.class);

    /**
     * 多节点执行的异步线程池。
     * 拒绝策略使用AbortPolicy（抛异常），避免CallerRunsPolicy在前端Netty EventLoop线程中同步执行导致整个eventLoop阻塞180秒。
     * 满载时上层会捕获RejectedExecutionException并返回错误给客户端。
     */
    private static final ThreadPoolExecutor multiNodeExecutor = new ThreadPoolExecutor(5, 100, 180L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("multi_node_executor-%d").build(), new ThreadPoolExecutor.AbortPolicy());

    /**
     * 全局统一的 sessionId 生成器（AtomicLong），保证进程内唯一递增。
     */
    private static final AtomicLong sessionIdGenerator = new AtomicLong();

    /**
     * 会话创建时间（{@code SystemClock.now()}，毫秒），用于统计与超时判定。
     */
    private final long createTime = SystemClock.now();

    /**
     * 当前命令已返回的数据行计数。{@link #onFinish()} 时归零。volatile 保证后端回调线程与 EventLoop 间可见性。
     */
    private volatile int dataRowsCount;

    /**
     * 当前命令的受影响行数累计（OK 包累加）。volatile，{@link #onFinish()} 时归零。
     */
    private volatile int affectRowsCount;

    /**
     * 当前命令已向客户端发送的字节数（tx），用于流量统计。volatile，{@link #onFinish()} 时归零。
     */
    private volatile long txBytes;

    /**
     * 当前命令从客户端接收的字节数（rx），用于流量统计。volatile，{@link #onFinish()} 时归零。
     */
    private volatile long rxBytes;

    /**
     * 当前命令是否执行失败的标记，初值 true。任一回调收到 ErrorPacket 或 fail message 时置为 false，
     * 触发 {@link #onFinish()} 中的错误 SQL 上报。volatile。
     */
    private volatile boolean isExeSuccess = true;

    /**
     * 是否已通过认证。false 时仅处理 auth 握手；true 后按命令分发。volatile。
     */
    private volatile boolean isLogon;

    /**
     * 绑定的前端 channel 上下文，不可变。后端回调线程通过它向前端写回数据。
     */
    private final ChannelHandlerContext ctx;

    /**
     * 会话唯一 ID（由 {@link #sessionIdGenerator} 分配），同时作为 MySQL 协议握手包中的 connectionId 返回给客户端。
     */
    private final long id;

    /**
     * 客户端登录用户名（auth 包中携带），用于鉴权与统计。
     */
    private String clientUser;

    /**
     * 客户端来源 IP，从 channel remoteAddress 解析，用于统计与审计。
     */
    private String clientHost;

    /**
     * 客户端来源端口。
     */
    private int clientPort;

    /**
     * 当前 database（USE 命令或握手时指定），用于 SQL 解析时确定默认 schema。
     */
    private String database;

    /**
     * 认证种子（20 字节），{@link #sendHandshake} 时生成发给客户端，{@link #auth} 校验密码后清空（置 null）防泄露。
     */
    private byte[] authSeed;

    /**
     * 最近一次后端响应/客户端活动的时间戳（毫秒），由 {@link #updateLastResponseTime} 在 channelRead 时刷新，供空闲检测使用。
     */
    private long lastResponseTime;

    /**
     * 当前命令开始执行的时间戳（毫秒），在 {@link #query} 入口记录，用于计算执行耗时。
     */
    private long lastRequestTime;

    /**
     * 当前命令的 SQL 解析结果，{@link #query} 时赋值，{@link #onFinish()} 时置 null。
     */
    private SqlParseResult sqlParseResult;

    /**
     * 单节点命令绑定的 sqlInfo（多节点命令不使用此字段，改由 {@link ProxyMultiNodeHandler} 管理 sqlInfoList）。
     * {@link #onFinish()} 时置 null。
     */
    private SqlParseResult.SqlInfo sqlInfo;

    /**
     * 客户端握手包中携带的字符集 collation 索引（如 255=utf8mb4_general_ci），用于后端连接字符集协商。
     */
    private int charsetIndex;

    /**
     * 构造会话，分配全局唯一 sessionId 并绑定前端 channel 上下文。
     *
     * @param ctx 前端 channel 的 {@link ChannelHandlerContext}
     */
    public ProxySession(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.id = sessionIdGenerator.incrementAndGet();
    }

    /**
     * @return 客户端登录用户名
     */
    public String getClientUser() {
        return clientUser;
    }

    /**
     * @param clientUser 客户端登录用户名
     */
    public void setClientUser(String clientUser) {
        this.clientUser = clientUser;
    }

    /**
     * @return 客户端来源 IP
     */
    public String getClientHost() {
        return clientHost;
    }

    /**
     * @param clientHost 客户端来源 IP
     */
    public void setClientHost(String clientHost) {
        this.clientHost = clientHost;
    }

    /**
     * @return 客户端来源端口
     */
    public int getClientPort() {
        return clientPort;
    }

    /**
     * @param clientPort 客户端来源端口
     */
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    /**
     * @return 会话创建时间（毫秒）
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * @return 全局唯一 sessionId（同时作为 MySQL connectionId）
     */
    public long getId() {
        return id;
    }

    /**
     * @return 最近一次活动时间（毫秒）
     */
    public long getLastResponseTime() {
        return lastResponseTime;
    }

    /**
     * @return 当前命令开始执行时间（毫秒）
     */
    public long getLastRequestTime() {
        return lastRequestTime;
    }

    /**
     * @return 是否已通过认证
     */
    public boolean isLogon() {
        return isLogon;
    }

    /**
     * 获取客户端信息（实现 {@link MySqlSessionCallback}），返回客户端 IP，供后端 session 记录来源。
     *
     * @return 客户端 IP
     */
    @Override
    public String getClientInfo() {
        return clientHost;
    }

    /**
     * 后端回调：收到 OK 包。累加 affectedRows、累计 tx 字节数后，将原始 ByteBuf 引用计数 +1 直接透传给前端 channel（零拷贝）。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整 4 字节包头 + payload 的 ByteBuf（调用方保证 retain 语义）
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        OkPacket okPacket = new OkPacket();
        okPacket.readPayLoad(buf);
        affectRowsCount += okPacket.affectedRows;
        buf.resetReaderIndex();
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到 Error 包。透传给前端并标记本次命令失败，触发 {@link #onFinish()} 中的错误 SQL 上报。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write(buf.retain());
        isExeSuccess = false;
    }

    /**
     * 后端回调：收到 ResultSet Header 包（结果集开头，携带列数）。透传给前端。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到 Field 包（列定义）。透传给前端。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到字段定义结束的 EOF 包。透传给前端。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到行数据包。累加行数后透传给前端。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        dataRowsCount++;
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到结果集结束的 EOF 包。透传给前端。
     *
     * @param packetId MySQL 协议 packetId
     * @param buf      包含完整包的 ByteBuf
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
        txBytes += buf.readableBytes();
        ctx.write(buf.retain());
    }

    /**
     * 后端回调：收到失败信息（非 ErrorPacket 形式，如连接异常等）。标记失败并写入 sqlParseResult 供统计使用。
     *
     * @param errorNo MySQL 错误号
     * @param info    错误信息
     */
    @Override
    public void onMysqlFailMessage(int errorNo, String info) {
        isExeSuccess = false;
        if (sqlParseResult != null) {
            sqlParseResult.setErrorInfo(errorNo, info);
        }
    }

    /**
     * 后端回调：当前命令在所有后端节点上执行完毕。
     * <p>
     * 若执行失败，上报错误 SQL；随后将本次命令的统计计数器与中间状态全部归零以复用会话。
     * 最后调用 {@code ctx.flush()} 将积攒的 write 一次性发往前端（必须最后调用，否则会在统计归零前把后续包计入下一条命令）。
     */
    @Override
    public void onFinish() {
        //开始统计数据了。
        if (!isExeSuccess && sqlInfo != null) {
            long now = SystemClock.now();
            StatsManager.reportErrorSql(clientHost, sqlInfo.getClusterId(), 0, sqlInfo.getDatabase(), sqlInfo.getTable(), sqlInfo.getNewSql(), this.sqlParseResult.getSqlType(),
                    Math.max(dataRowsCount, affectRowsCount), txBytes, rxBytes, now - lastRequestTime, now, this.sqlParseResult.getErrorCode(),
                    this.sqlParseResult.getErrorMessage(), null);
        }
        //数据归零
        sqlParseResult = null;
        sqlInfo = null;
        isExeSuccess = true;
        this.dataRowsCount = 0;
        this.affectRowsCount = 0;
        this.rxBytes = 0;
        this.txBytes = 0;
        //最后才能flush，否则会出问题！！！
        this.ctx.flush();
    }

    /**
     * @return 当前 database 名
     */
    public String getDatabase() {
        return database;
    }

    /**
     * 设置 database。
     * database 名只允许合法标识符字符（防止 "use xxx" 拼接的 SQL 注入），获取连接 + 下发命令异步执行避免阻塞 EventLoop。
     *
     * @param database 新的 database 名（仅允许 [A-Za-z0-9_]+，否则拒绝并告警）
     */
    public void setDatabase(String database) {
        if (StringUtils.isNotBlank(database)) {
            //标识符白名单校验，防止"use "+database的SQL注入（如 database="a; DROP DATABASE x"）
            if (!database.matches("[A-Za-z0-9_]+")) {
                logger.warn("setDatabase(): 非法数据库名[{}]，已拒绝", database);
                return;
            }
            this.database = database;
            final String dbName = this.database;
            try {
                multiNodeExecutor.execute(() -> {
                    MySqlSession mySqlSession = MySqlClient.getMySqlSession(MydbProxyConfigService.getProxyConfig().getBaseCluster(), true);
                    if (mySqlSession != null) {
                        mySqlSession.addCommand(this, "use " + dbName);
                    } else {
                        logger.warn("setDatabase(): 无法获取基础集群的mysqlSession, database={}", dbName);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                logger.warn("setDatabase(): 线程池满，database={}", dbName);
            }
        }
    }

    /**
     * 在连接建立后向客户端发送 MySQL Initial Handshake Packet（协议版本 / serverVersion / connectionId / 种子 / 能力位 / 插件名）。
     * 生成 20 字节随机种子并保存到 {@link #authSeed}，供后续 {@link #auth} 校验客户端密码回复时使用。
     *
     * @param ctx 前端 channel 上下文
     */
    public void sendHandshake(ChannelHandlerContext ctx) {
        // 生成认证数据
        byte[] rand1 = RandomUtils.randomBytes(8);
        byte[] rand2 = RandomUtils.randomBytes(12);
        // 保存认证数据
        byte[] seed = new byte[rand1.length + rand2.length];
        System.arraycopy(rand1, 0, seed, 0, rand1.length);
        System.arraycopy(rand2, 0, seed, rand1.length, rand2.length);
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
        hs.writeToChannel(ctx);
        ctx.flush();
    }

    /**
     * 处理客户端的 Handshake Response（认证包）。
     * 校验流程：拒绝压缩协议 -> 校验用户名存在 -> 根据 authPluginName（caching_sha2_password / mysql_native_password）
     * 用保存的 seed 重新 scramble 与客户端回复比对（{@link MessageDigest#isEqual} 防时序攻击）-> 通过则设置 isLogon=true、
     * 记录用户名/字符集/database、清空 authSeed、回写 OK 包；任一失败则回写 Error 并关闭连接。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 客户端握手响应包
     */
    public void auth(ChannelHandlerContext ctx, ByteBuf buf) {
        MydbProxyConfig config = MydbProxyConfigService.getProxyConfig();
        AuthHandshakeResponsePacket authPacket = new AuthHandshakeResponsePacket();
        authPacket.readPayLoad(buf);
        String authPluginName = authPacket.authPluginName;
        long capabilities = authPacket.clientCapability;

        //如果客户端开启压缩，那么直接返回不支持。
        if (MySQLCapability.isClientCompress(capabilities)) {
            onProxyFailMessage(ctx, MySqlErrorCode.ER_NET_UNCOMPRESS_ERROR, "Can not use compression protocol!");
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

        if (!Strings.CS.equals(config.getUsername(), authPacket.username)) {
            onProxyFailMessage(ctx, MySqlErrorCode.ER_ACCESS_DENIED_ERROR, "Access denied for user '" + authPacket.username + "', because user is not exists! ");
            onFinish();
            ctx.close();
            return;
        }

        // 检查密码匹配。
        byte[] encryptPass = null;
        if (authPluginName.equals(CachingSha2PasswordPlugin.PROTOCOL_PLUGIN_NAME)) {
            encryptPass = CachingSha2PasswordPlugin.scrambleCachingSha2(config.getPassword(), this.authSeed);
        } else {
            encryptPass = MySqlNativePasswordPlugin.scramble411(config.getPassword(), this.authSeed);
        }

        if (!MessageDigest.isEqual(encryptPass, authPacket.password)) {
            onProxyFailMessage(ctx, MySqlErrorCode.ER_PASSWORD_NO_MATCH, "Access denied for user '" + authPacket.username + "', because password is error ");
            onFinish();
            ctx.close();
            return;
        }

        // 检查scheme权限
        if (StringUtils.isNotBlank(authPacket.database)) {
            this.database = authPacket.database;
        }
        // 设置字符集编码
        this.charsetIndex = (authPacket.charsetIndex & 0xff);
        //设置session用户
        this.clientUser = authPacket.username;
        this.isLogon = true;
        this.authSeed = null;
        OkPacket.writeAuthOkToChannel(ctx);
    }

    /**
     * 处理 COM_INIT_DB 命令（客户端切换 database）。
     * 解析出目标 database 名后委托给 {@link #setDatabase}（含标识符白名单校验与异步下发）。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void initDB(ChannelHandlerContext ctx, ByteBuf buf) {
        CommandPacket cmd = new CommandPacket();
        cmd.readPayLoad(buf);
        //切换Schema
        this.setDatabase(cmd.arg);
    }

    /**
     * 处理 COM_QUERY 命令（核心入口）。
     * <p>
     * 流程：读取 SQL -> {@link SqlParser#parse()} 路由解析 ->
     * <ul>
     *   <li>解析出错：回写 Error 并 {@link #onFinish}。</li>
     *   <li>单节点（sqlInfo 非空）：提交到 {@link #multiNodeExecutor} 异步获取 MySqlSession 并下发命令；线程池满则回写 "Proxy busy"。</li>
     *   <li>多节点（sqlInfoList 非空）：提交 {@link ProxyMultiNodeHandler} 到异步线程池聚合结果。</li>
     * </ul>
     * 异步化的目的：避免 getMySqlSession()（可能等待空闲连接）阻塞 Netty EventLoop。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包（payload 为 SQL 文本）
     */
    public void query(ChannelHandlerContext ctx, ByteBuf buf) {
        rxBytes += buf.readableBytes();
        lastRequestTime = SystemClock.now();
        //如果schema没有任何表分区定义，则直接转发到默认库。
        //读取sql
        CommandPacket cmd = new CommandPacket();
        cmd.readPayLoad(buf);
        String sql = cmd.arg;
        if (logger.isTraceEnabled()) {
            logger.trace("Receive client[{}] SQL: {}", this.clientHost, sql);
        }
        //根据解析结果判定，当前支持1.单实例执行；2.多实例执行
        SqlParser parser = new SqlParser(this, sql);
        sqlParseResult = parser.parse();
        //sql解析后，routeResult=null的，可能已经在parser里处理过了。
        if (sqlParseResult.hasError()) {
            if (sqlParseResult.getErrorCode() > 0) {
                //error code>0的，发送错误信息。
                onProxyFailMessage(ctx, sqlParseResult.getErrorCode(), sqlParseResult.getErrorMessage());
            }
            onFinish();
            return;
        }
        //压测时，可直接返回ok包的。
        if (sqlParseResult.getSqlInfo() != null) {
            //单实例执行：获取连接+下发命令必须异步执行，禁止在Netty EventLoop线程中阻塞getMySqlSession()。
            this.sqlInfo = sqlParseResult.getSqlInfo();
            final SqlParseResult.SqlInfo sqlInfo = this.sqlInfo;
            final boolean isMasterQuery = sqlParseResult.isMasterQuery();
            final int sqlType = sqlParseResult.getSqlType();
            try {
                multiNodeExecutor.execute(() -> {
                    MySqlSession mySqlSession = MySqlClient.getMySqlSession(sqlInfo.getClusterId(), isMasterQuery);
                    if (mySqlSession == null) {
                        logger.warn("MySQL Cluster[{}]无法找到合适的mysqlSession!", sqlInfo.getClusterId());
                        onProxyFailMessage(ctx, MySqlErrorCode.ERR_NO_ROUTE_NODE, "Can't route to mysqlCluster!");
                        onFinish();
                        return;
                    }
                    mySqlSession.addCommand(this, sqlInfo.getDatabase(), sqlInfo.getTable(), sqlInfo.getNewSql(), sqlType);
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                logger.warn("单节点SQL执行被拒绝（线程池满），client={}", this.clientHost);
                onProxyFailMessage(ctx, MySqlErrorCode.ERR_CONN_NOT_ALIVE, "Proxy busy, try again later!");
                onFinish();
            }
        } else {
            //多实例执行使用CountDownLatch同步返回所有结果后，再执行转发，可能会导致阻塞。
            try {
                multiNodeExecutor.submit(new ProxyMultiNodeHandler(this.clientHost, this.ctx, sqlParseResult));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                logger.warn("多节点SQL执行被拒绝（线程池满），client={}", this.clientHost);
                onProxyFailMessage(ctx, MySqlErrorCode.ERR_CONN_NOT_ALIVE, "Proxy busy, try again later!");
                onFinish();
            }
        }
    }

    /**
     * 处理 COM_PING，直接回写 OK 包（不路由到后端，纯探活）。
     *
     * @param ctx 前端 channel 上下文
     */
    public void ping(ChannelHandlerContext ctx) {
        OkPacket.writeOkToChannel(ctx);
    }

    /**
     * 处理 COM_QUIT，关闭前端 channel（触发 {@link ProxyDataHandler#channelInactive} 清理）。
     *
     * @param ctx 前端 channel 上下文
     */
    public void close(ChannelHandlerContext ctx) {
        ctx.close();
    }

    /**
     * 处理 COM_PROCESS_KILL，当前实现直接返回不支持。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void kill(ChannelHandlerContext ctx, ByteBuf buf) {
        onProxyFailMessage(ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT KILL!");
        onFinish();
    }

    /**
     * 处理 COM_STMT_PREPARE（预编译），当前实现直接返回不支持。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void stmtPrepare(ChannelHandlerContext ctx, ByteBuf buf) {
        onProxyFailMessage(ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT STMT_PREPARE!");
        onFinish();
    }

    /**
     * 处理 COM_STMT_EXECUTE（执行预编译），当前实现直接返回不支持。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void stmtExecute(ChannelHandlerContext ctx, ByteBuf buf) {
        onProxyFailMessage(ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT STMT_EXECUTE!");
        onFinish();
    }

    /**
     * 处理 COM_STMT_CLOSE（关闭预编译），当前实现直接返回不支持。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void stmtClose(ChannelHandlerContext ctx, ByteBuf buf) {
        onProxyFailMessage(ctx, MySqlErrorCode.ERR_NOT_SUPPORTED, "NOT SUPPORT STMT_CLOSE!");
        onFinish();
    }

    /**
     * 处理自定义心跳命令（CMD_HEARTBEAT=64），回写 OK 包并触发 onFinish 刷盘。
     *
     * @param ctx 前端 channel 上下文
     * @param buf 命令包
     */
    public void heartbeat(ChannelHandlerContext ctx, ByteBuf buf) {
        OkPacket.writeOkToChannel(ctx);
        onFinish();
    }

    /**
     * 向前端回写一条 Error 包（packetId=1），并标记本次命令失败。
     *
     * @param ctx     前端 channel 上下文
     * @param errorNo MySQL 错误号
     * @param info    错误信息（会原样回传给客户端，不要拼接敏感信息）
     */
    public void onProxyFailMessage(ChannelHandlerContext ctx, int errorNo, String info) {
        isExeSuccess = false;
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.packetId = 1;
        errorPacket.errorNo = errorNo;
        errorPacket.message = info;
        errorPacket.writeToChannel(ctx);
        ctx.flush();
    }

    /**
     * 在 {@link ProxyDataHandler#channelRead} 入口调用，刷新 {@link #lastResponseTime} 用于空闲检测。
     */
    public void updateLastResponseTime() {
        this.lastResponseTime = SystemClock.now();
    }
}
