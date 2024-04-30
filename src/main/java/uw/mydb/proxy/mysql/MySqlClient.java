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
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.common.report.MysqlConnStats;
import uw.mydb.common.conf.MysqlClusterConfig;
import uw.mydb.common.conf.MysqlServerConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * mysql客户端。
 *
 * @author axeon
 */
public class MySqlClient {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( MySqlClient.class );

    /**
     * poolMap。
     */
    private static AbstractChannelPoolMap<MysqlServerConfig, MySqlPool> channelPoolMap;

    /**
     * acceptor线程。
     */
    private static EventLoopGroup eventLoopGroup = null;

    /**
     * 后台调度任务。
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
     * 获得mysql session。
     *
     * @param clusterId
     * @return
     */
    public static MySqlSession getMySqlSession(long clusterId, boolean isMaster) {
        MysqlClusterConfig clusterConfig = MydbProxyConfigService.getMysqlCluster( clusterId );
        if (clusterConfig == null) {
            return null;
        }
        MysqlServerConfig mysqlServerConfig = clusterConfig.fetchServerConfig( isMaster );
        MySqlPool channelPool = channelPoolMap.get( mysqlServerConfig );
        Future<Channel> channelFuture = channelPool.acquire();
        MySqlSession mySqlSession = null;
        try {
            mySqlSession = channelFuture.get().attr( MySqlHandler.MYSQL_SESSION ).get();
            //绑定连接池，这个非常重要。
            mySqlSession.bindChannelPool( channelPool );
        } catch (Throwable e) {
            logger.error( e.toString(), e );
        }
        return mySqlSession;
    }

    /**
     * 启动服务器
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
                Iterator<Map.Entry<MysqlServerConfig, MySqlPool>> iterator = channelPoolMap.iterator();
                while (iterator.hasNext()) {
                    Map.Entry<MysqlServerConfig, MySqlPool> kv = iterator.next();
                    MysqlServerConfig config = kv.getKey();
                    MySqlPool pool = kv.getValue();
                    if (logger.isDebugEnabled()) {
                        logger.debug( "MySql[{}]Pool run housekeeping...current idleConn[{}],busyConn[{}]", config.toString(), pool.getIdleConnNum(), pool.getBusyConnNum() );
                    }
                    pool.housekeeping();
                }
            }
        }, 60, 60, TimeUnit.SECONDS );
        logger.info( "MySqlClient started!" );
    }

    /**
     * 获得mysql连接列表。
     * mysqlId,busyConnNum,idleConnNum
     *
     * @return
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
     * 关闭服务器。
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
