package uw.mydb.proxy.mysql;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.MysqlClusterConfig;
import uw.mydb.common.conf.MysqlServerConfig;
import uw.mydb.common.report.MysqlConnStats;
import uw.mydb.proxy.conf.MydbProxyConfigService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * MySQL 后端连接客户端（静态门面），管理所有 MySQL cluster/server 的连接池并对外提供 session 获取入口。
 *
 * <p>职责：
 * <ul>
 *   <li>启动/停止 NIO EventLoopGroup 与按 {@link MysqlServerConfig} 维度建池的
 *       {@link AbstractChannelPoolMap}；</li>
 *   <li>对外暴露 {@link #getMySqlSession(long, boolean)}：按 clusterId 路由到主/从节点的
 *       {@link MySqlPool}，acquire channel 并返回对应的 {@link MySqlSession}；</li>
 *   <li>启动后台 housekeeping 定时任务（默认 60s 一次），调用每个 pool 的
 *       {@link MySqlPool#housekeeping()} 清理超时/超龄连接；</li>
 *   <li>对外暴露 {@link #getMysqlConnStats()} 汇总各 pool 的 busy/idle 连接数。</li>
 * </ul>
 *
 * <h3>线程安全模型</h3>
 * 本类为静态单例式门面，内部 EventLoopGroup 与 scheduledExecutorService 均在 {@link #start()} 中创建，
 * 在 {@link #stop()} 中关闭。channelPoolMap 内部线程安全。
 *
 * <h3>getMySqlSession 阻塞语义（重要）</h3>
 * {@link #getMySqlSession} 内部通过 {@code Future.get(30, SECONDS)} 阻塞等待 acquire 完成，
 * <b>禁止在 Netty EventLoop 线程中调用</b>，否则会阻塞同 loop 上其它连接的 IO，造成死锁/饿死。
 * 调用方必须确保在独立线程（如业务线程池、multiNodeExecutor 等）中调用，超时后会 cancel future
 * 防止 channel 泄漏。
 *
 * @author axeon
 */
public class MySqlClient {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( MySqlClient.class );

    /**
     * 按 {@link MysqlServerConfig} 维度建池的 pool map，每个 server 对应一个独立的 {@link MySqlPool}。
     */
    private static AbstractChannelPoolMap<MysqlServerConfig, MySqlPool> channelPoolMap;

    /**
     * NIO EventLoopGroup（实际为 {@link NioEventLoopGroup}），线程名 mysql-event-%d，
     * 作为所有 MySQL 连接 channel 的 EventLoopGroup。
     */
    private static EventLoopGroup eventLoopGroup = null;

    /**
     * 后台 housekeeping 调度器（单线程 daemon），周期性触发各 pool 的 {@link MySqlPool#housekeeping()}。
     */
    private static ScheduledExecutorService scheduledExecutorService;

//    public static void main(String[] args) throws InterruptedException {
//        MySqlClient.start();
//        //mysql配置缓存 key: mysql clusterId value: mysqlClusterConfig
//        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), new CacheDataLoader<Long, MysqlClusterConfig>() {
//            @Override
//            public MysqlClusterConfig load(Long key) throws Exception {
//                return null;
//            }
//        }, (key, oldValue, newValue) -> {
//            //此处要重新加载mysqlCluster信息。
//        } );
//        MysqlServerConfig mysqlServerConfig = new MysqlServerConfig();
//        mysqlServerConfig.setHost( "dev.xili.pub" );
//        mysqlServerConfig.setPort( 3308 );
//        mysqlServerConfig.setWeight( 1 );
//        mysqlServerConfig.setUsername( "root" );
//        mysqlServerConfig.setPassword( "mysqlRootPassword" );
//        ArrayList<MysqlServerConfig> serverList = new ArrayList();
//        serverList.add( mysqlServerConfig );
//        MysqlClusterConfig mysqlClusterConfig = new MysqlClusterConfig();
//        mysqlClusterConfig.setId( 1 );
//        mysqlClusterConfig.setServerList( serverList );
//        mysqlClusterConfig.initServerWeightList();
//        FusionCache.put( MysqlClusterConfig.class, mysqlClusterConfig.getId(), mysqlClusterConfig, true );
//        new SingleListTask( 1, new LocalCmdCallback<ArrayList<String>>() {
//            @Override
//            public void onSuccess(ArrayList<String> data) {
//                for (String line : data) {
//                    logger.info( "db:{}", line );
//                }
//            }
//
//            @Override
//            public void onFailure(int errorNo, String message) {
//                logger.error( "errorNo[{}]:{}", errorNo, message );
//            }
//        } ).run( "show databases" );
////        Thread.sleep( 3000 );
////        stop();
//    }

    /**
     * 获取一个 MySQL session：按 clusterId 与主从标记路由到目标 server，从对应 {@link MySqlPool}
     * acquire channel，并返回 channel 上挂载的 {@link MySqlSession}，同时注入 pool 引用。
     *
     * <p><b>阻塞语义（重要）</b>：本方法会调用 {@code channelFuture.get(30, SECONDS)} 同步阻塞等待
     * 连接池 acquire，<b>禁止在 Netty EventLoop 线程中调用</b>，否则会饿死同 loop 上的其它连接 IO。
     * 调用方必须确保在独立线程（如 multiNodeExecutor 或业务线程池）中调用。
     *
     * <p>超时处理：捕获 {@link TimeoutException} 时会 {@code cancel(true)} future，避免连接池
     * 在 future 最终完成时把 channel 放入 busySet 但无人 release 造成连接泄漏。
     *
     * @param clusterId 目标 MySQL cluster id
     * @param isMaster  true 走主节点，false 走从节点（按权重负载均衡）
     * @return 已绑定 pool 的 session，或 null（cluster 不存在/无可用节点/acquire 超时或异常）
     */
    public static MySqlSession getMySqlSession(long clusterId, boolean isMaster) {
        MysqlClusterConfig clusterConfig = MydbProxyConfigService.getMysqlCluster( clusterId );
        if (clusterConfig == null) {
            return null;
        }
        MysqlServerConfig mysqlServerConfig = clusterConfig.fetchServerConfig( isMaster );
        if (mysqlServerConfig == null) {
            logger.error( "获取MySQL连接失败：无可用服务器节点[clusterId={}, isMaster={}]", clusterId, isMaster );
            return null;
        }
        MySqlPool channelPool = channelPoolMap.get( mysqlServerConfig );
        Future<Channel> channelFuture = channelPool.acquire();
        MySqlSession mySqlSession = null;
        try {
            mySqlSession = channelFuture.get( 30, TimeUnit.SECONDS ).attr( MySqlHandler.MYSQL_SESSION ).get();
            //绑定连接池，这个非常重要。
            mySqlSession.bindChannelPool( channelPool );
        } catch (TimeoutException e) {
            //超时后必须cancel future，否则连接池在future最终完成时会把channel放入busySet但无人release，导致连接泄漏。
            channelFuture.cancel( true );
            logger.error( "获取MySQL连接超时[clusterId={}]: {}", clusterId, e.getMessage() );
        } catch (Throwable e) {
            logger.error( e.toString(), e );
        }
        return mySqlSession;
    }

    /**
     * 启动 MySQL 客户端：创建 NIO EventLoopGroup（连接超时 10s、TCP_NODELAY），
     * 初始化按 {@link MysqlServerConfig} 建池的 {@link AbstractChannelPoolMap}（pool 参数全部来自
     * serverConfig：connMin/connMax/connIdleTimeout/connBusyTimeout/connMaxAge），并启动 60s 间隔的
     * housekeeping 调度任务（daemon 线程 mysql-housekeeping-task）。
     */
    public static void start() {
        eventLoopGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mysql-event-%d" ).build() );
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group( eventLoopGroup ).channel( NioSocketChannel.class ).option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000 ).option( ChannelOption.TCP_NODELAY, true );
        channelPoolMap = new AbstractChannelPoolMap<>() {
            @Override
            protected MySqlPool newPool(MysqlServerConfig mysqlServerConfig) {
                return new MySqlPool( bootstrap.remoteAddress( mysqlServerConfig.getHost(), mysqlServerConfig.getPort() ).clone(), new MysqlPoolHandler( mysqlServerConfig ),
                        mysqlServerConfig.getConnMin(), mysqlServerConfig.getConnMax(), mysqlServerConfig.getConnIdleTimeout() * 1000L,
                        mysqlServerConfig.getConnBusyTimeout() * 1000L, mysqlServerConfig.getConnMaxAge() * 1000L );
            }
        };
        //设置后台调度任务。
        scheduledExecutorService = Executors.newScheduledThreadPool( 1, r -> {
            Thread thread = new Thread( r );
            thread.setName( "mysql-housekeeping-task" );
            thread.setDaemon( true );
            return thread;
        } );
        //连接池维护。
        scheduledExecutorService.scheduleAtFixedRate( () -> {
            if (channelPoolMap != null) {
                logger.debug( "MySqlPool start housekeeping schedule task..." );
                for (Map.Entry<MysqlServerConfig, MySqlPool> kv : channelPoolMap) {
                    MysqlServerConfig config = kv.getKey();
                    MySqlPool pool = kv.getValue();
                    if (logger.isDebugEnabled()) {
                        logger.debug("MySql[{}]Pool run housekeeping...current idleConn[{}],busyConn[{}]", config.toString(), pool.getIdleConnNum(), pool.getBusyConnNum());
                    }
                    pool.housekeeping();
                }
            }
        }, 60, 60, TimeUnit.SECONDS );
        logger.info( "MySqlClient started!" );
    }

    /**
     * 汇总所有 server 连接池的统计信息，用于监控/上报。
     *
     * @return 含 pool 总数、总 busy/idle 连接数及每个 mysqlId 的 [busyConnNum, idleConnNum] 明细的
     *         {@link MysqlConnStats}
     */
    public static MysqlConnStats getMysqlConnStats() {
        long sumBusyConnNum = 0, sumIdleConnNum = 0;
        List<long[]> mysqlConnList = new ArrayList<>();
        Iterator<Map.Entry<MysqlServerConfig, MySqlPool>> iterator = channelPoolMap.iterator();
        while (iterator.hasNext()) {
            Map.Entry<MysqlServerConfig, MySqlPool> kv = iterator.next();
            MysqlServerConfig config = kv.getKey();
            MySqlPool pool = kv.getValue();
            int busyConnNum = pool.getBusyConnNum();
            int idleConnNum = pool.getIdleConnNum();
            sumBusyConnNum += busyConnNum;
            sumIdleConnNum += idleConnNum;
            mysqlConnList.add( new long[]{config.getId(), busyConnNum, idleConnNum} );
        }
        return new MysqlConnStats( channelPoolMap.size(), sumBusyConnNum, sumIdleConnNum, mysqlConnList );
    }

    /**
     * 关闭 MySQL 客户端：先 shutdownNow housekeeping 调度器，再 shutdownGracefully EventLoopGroup。
     * 注意：此方法不会主动关闭各 pool 中的 idle/busy channel，调用方应在 stop 前显式遍历 poolMap.close()。
     */
    public static void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }

        logger.info( "MySqlClient stopped!" );
    }

}
