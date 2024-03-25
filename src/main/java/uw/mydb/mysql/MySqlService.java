package uw.mydb.mysql;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.mysql.util.ConcurrentBag;
import uw.mydb.protocol.util.MySqlErrorCode;
import uw.mydb.util.SystemClock;
import uw.mydb.vo.MysqlServerConfig;

import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static uw.mydb.mysql.util.ConcurrentBag.IConcurrentBagEntry.STATE_NORMAL;
import static uw.mydb.mysql.util.ConcurrentBag.IConcurrentBagEntry.STATE_USING;
import static uw.mydb.util.SystemClock.elapsedMillis;

/**
 * mysql服务。
 * 用来维护一个mysql服务器中所有的session。
 *
 * @author axeon
 */
public class MySqlService implements ConcurrentBag.IBagStateListener {

    private static final Logger logger = LoggerFactory.getLogger( MySqlService.class );
    /**
     * 新建连接服务。
     */
    private static ThreadPoolExecutor addSessionExecutor;
    /**
     * 当前启动状态.
     */
    private final AtomicBoolean status = new AtomicBoolean( false );
    /**
     * 异步创建线程实例。
     */
    private final SessionCreator SESSION_CREATOR = new SessionCreator();
    /**
     * acceptor线程。
     */
    private EventLoopGroup group = null;

    /**
     * bootstrap实例。
     */
    private Bootstrap bootstrap = new Bootstrap();

    /**
     * mysql配置信息。
     */
    private MysqlServerConfig config;
    /**
     * 配置组信息
     */
    private MySqlClusterService mysqlGroupService;

    /**
     * 是否是slave主机。
     */
    private boolean isSlaveNode;
    /**
     * 存储可用连接池的地方。
     */
    private ConcurrentBag<MySqlSession> sessionBag;
    /**
     * 正在连接中的session数量。
     */
    private AtomicInteger pendingCreateCount = new AtomicInteger( 0 );

    /**
     * 连接建立数。
     */
    private AtomicInteger connectionCreateCount = new AtomicInteger( 0 );

    /**
     * 连接建立错误计数。
     */
    private AtomicInteger connectionCreateErrorCount = new AtomicInteger( 0 );

    /**
     * 是否活着。
     */
    private boolean isAlive = true;

    /**
     * service name。
     */
    private String name;


    public MySqlService(MySqlClusterService mysqlGroupService, MysqlServerConfig config) {
        this.config = config;
        this.mysqlGroupService = mysqlGroupService;
        this.name = new StringBuilder().append( this.config.getUser() ).append( '@' ).append( this.config.getHost() ).append( ':' ).append( this.config.getPort() ).toString();
        this.sessionBag = new ConcurrentBag<>( this );
    }

    /**
     * 获得服务信息。
     *
     * @return
     */
    public String getName() {
        return this.name;
    }

    /**
     * 获得groupService
     *
     * @return
     */
    public String getGroupName() {
        return mysqlGroupService.getClusterName();
    }

    /**
     * 启动服务。
     *
     * @return
     */
    public boolean start() {
        if (status.compareAndSet( false, true )) {
            group = new NioEventLoopGroup( config.getThreadNum(), new ThreadFactoryBuilder().setNameFormat( "mysql_" + name + "-%d" ).build() );
            bootstrap.group( group )
                    .channel( NioSocketChannel.class )
                    .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000 )
                    .option( ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT )
                    .option( ChannelOption.TCP_NODELAY, false )
                    .handler( new MySqlDataHandlerFactory() );
            addSessionExecutor = new ThreadPoolExecutor( 1, 10, 20, SECONDS, new SynchronousQueue<>(),
                    new ThreadFactoryBuilder().setNameFormat( "mysql_add_session-%d" ).setDaemon( true ).build(), new ThreadPoolExecutor.DiscardPolicy() );
            return true;
        } else {
            return false;
        }
    }

    /**
     * 关闭服务。
     *
     * @return
     */
    public boolean stop() {
        if (status.compareAndSet( true, false )) {
            sessionBag.close();
            addSessionExecutor.shutdown();
            group.shutdownGracefully();
            return true;
        } else {
            return false;
        }
    }

    /**
     * 初始化
     */
    public void init() {
    }

    /**
     * 是否存活。
     *
     * @return
     */
    public boolean isAlive() {
        return isAlive;
    }

    /**
     * 获得配置文件。
     *
     * @return
     */
    public MysqlServerConfig getConfig() {
        return config;
    }

    /**
     * 设置配置文件。
     *
     * @param config
     */
    public void setConfig(MysqlServerConfig config) {
        this.config = config;
    }

    /**
     * 是否是salve节点。
     *
     * @return
     */
    public boolean isSlaveNode() {
        return isSlaveNode;
    }

    /**
     * 设置是否slave节点状态。
     *
     * @param slaveNode
     */
    public void setSlaveNode(boolean slaveNode) {
        isSlaveNode = slaveNode;
    }

    /**
     * 获得一个可用的session。
     *
     * @return
     */
    public MySqlSession getSession(MySqlSessionCallback mysqlSessionCallback) {
        final long startTime = SystemClock.now();
        try {
            long timeout = 10_000;
            do {
                MySqlSession session = sessionBag.borrow( timeout );
                if (session == null) {
                    logger.warn( "!!!MySqlService[{}]({}) get session failed from pool by timeout!", this.getName(), this.sessionBag.size() );
                    continue; // We timed out... break and throw exception
                }
                //检查session状态。
                if (!session.isAlive()) {
                    //此处应尝试关闭。
                    sessionBag.reserve( STATE_USING, session );
                    closeSession( session, MySqlErrorCode.ERR_CONN_NOT_ALIVE, "check session is not alive!" );
                    continue;
                }
                //检查是否超过最大寿命。因为在后台检查中可能无法进入寿命检查状态。
                if (SystemClock.elapsedMillis( session.createTime, startTime ) > SECONDS.toMillis( config.getConnMaxAge() )) {
                    sessionBag.reserve( STATE_USING, session );
                    closeSession( session, MySqlErrorCode.ERR_NONE, "connection has maxAge timeout!" );
                    continue;
                }
                session.bind( mysqlSessionCallback );
                return session;
            } while (timeout >= elapsedMillis( startTime ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * 当前session总数。
     *
     * @return
     */
    public int getTotalSessions() {
        return sessionBag.size();
    }

    /**
     * 空闲session数量。
     *
     * @return
     */
    public int getIdleSessions() {
        return sessionBag.getCount( STATE_NORMAL );
    }

    /**
     * 等待线程数。
     */
    public int getAwaitingThreads() {
        return sessionBag.getWaitingThreadCount();
    }

    /**
     * 获得建立中的连接数。
     *
     * @return
     */
    public int getPendingCreateConnections() {
        return pendingCreateCount.get();
    }

    /**
     * 获得连接创建计数。
     *
     * @return
     */
    public int getConnectionCreateCount() {
        return connectionCreateCount.get();
    }

    /**
     * 获得连接创建错误计数。
     *
     * @return
     */
    public int getConnectionCreateErrorCount() {
        return connectionCreateErrorCount.get();
    }

    /**
     * 增加一个bagItem。
     * 为了防止频繁的添加创建session任务，使用pendingAddCount来校验。
     *
     * @param waiting
     */
    @Override
    public void addBagItem(int waiting) {
        if (pendingCreateCount.get() == 0) {
            addSessionExecutor.submit( SESSION_CREATOR );
        }
    }

    /**
     * 归还session。
     *
     * @param mysqlSession
     */
    public void requiteSession(MySqlSession mysqlSession) {
        sessionBag.requite( mysqlSession );
    }

    /**
     * 同步创建一个session。
     */
    private MySqlSession createSession(String msg) {
        MySqlSession session = null;
        pendingCreateCount.incrementAndGet();
        try {
            connectionCreateCount.incrementAndGet();
            ChannelFuture cf = bootstrap.connect( config.getHost(), config.getPort() );
            cf.addListener( new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.warn( "!!!MySqlService[{}]({}+{}) create session by {}, error msg: {}", getName(), sessionBag.size(), pendingCreateCount.get(), msg,
                                future.cause() );
                        connectionCreateErrorCount.incrementAndGet();
                    }
                }
            } );
            Channel channel = cf.channel();
            session = new MySqlSession( this, channel );
            channel.attr( MySqlDataHandler.MYSQL_SESSION ).set( session );
            cf.sync();
            logger.info( "MySqlService[{}]({}+{}) create session {} by {}", this.getName(), sessionBag.size(), pendingCreateCount.get(), session, msg );
        } catch (InterruptedException e) {
            logger.error( "!!!MySqlService[{}]({}+{}) create session by {}, error msg: {}", this.getName(), sessionBag.size(), pendingCreateCount.get(), msg, e.getMessage() );
            connectionCreateErrorCount.incrementAndGet();
        }
        //session建立阶段挂掉的，减去pending计数。
        if (session == null) {
            pendingCreateCount.decrementAndGet();
        }
        return session;
    }

    /**
     * 填充连接池。
     */
    private synchronized void fillPool() {
        addSessionExecutor.submit( SESSION_CREATOR );
    }

    /**
     * 异步创建连接线程。
     */
    public final class SessionCreator implements Runnable {

        @Override
        public void run() {
            while (shouldCreateAnotherSession()) {
                final MySqlSession session = createSession( "auto create" );
                if (session == null) {
                    break;
                }
            }
        }

        /**
         * 判断是否需要创建新的session。
         *
         * @return
         */
        private boolean shouldCreateAnotherSession() {
            return (getTotalSessions() + pendingCreateCount.get()) < config.getConnMax() &&
                    (sessionBag.getWaitingThreadCount() > pendingCreateCount.get() || (getIdleSessions()) < config.getConnMin());
        }

    }

    /**
     * 向bag中增加一个session。
     *
     * @param session
     */
    void initSession(MySqlSession session) {
        //验证结束，减去pending计数。
        pendingCreateCount.decrementAndGet();
        if (session != null) {
            sessionBag.add( session );
            logger.debug( "MySqlService[{}]({}+{}) inited session: {}", name, sessionBag.size(), pendingCreateCount.get(), session );
        }
    }

    /**
     * 永久关闭一个连接。
     *
     * @param session
     * @param closureReason reason to close
     */
    void closeSession(final MySqlSession session, final int closureCode, final String closureReason) {
        if (closureCode > 0) {
            logger.warn( "!!!MySqlService[{}]({}) close session {} by {}.", this.getName(), sessionBag.size(), session, closureReason );
            //一定要通知报错，通知之后，会自动解绑。
            session.failMessage( closureCode, closureReason );
        } else {
            logger.info( "MySqlService[{}]({}) close session {} by {}.", this.getName(), sessionBag.size(), session, closureReason );
        }
        //session必须解绑。
        session.unbind();
        if (sessionBag.remove( session )) {
        }
    }
}
