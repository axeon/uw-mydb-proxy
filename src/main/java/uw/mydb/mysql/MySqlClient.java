package uw.mydb.mysql;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.slf4j.LoggerFactory;
import uw.cache.CacheDataLoader;
import uw.cache.FusionCache;
import uw.mydb.mysql.task.LocalCmdCallback;
import uw.mydb.mysql.task.SingleListTask;
import uw.mydb.vo.MysqlClusterConfig;
import uw.mydb.vo.MysqlServerConfig;

import java.util.ArrayList;


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
    public static ChannelPoolMap<MysqlServerConfig, FixedChannelPool> poolMap;

    /**
     * acceptor线程。
     */
    private static EventLoopGroup eventLoopGroup = null;

    public static void main(String[] args) throws InterruptedException {
        MySqlClient.start();
        //mysql配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), new CacheDataLoader<Long, MysqlClusterConfig>() {
            @Override
            public MysqlClusterConfig load(Long key) throws Exception {
                return null;
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载mysqlCluster信息。
        } );
        MysqlServerConfig mysqlServerConfig = new MysqlServerConfig();
        mysqlServerConfig.setHost( "dev.xili.pub" );
        mysqlServerConfig.setPort( 3308 );
        mysqlServerConfig.setWeight( 1 );
        mysqlServerConfig.setUsername( "root" );
        mysqlServerConfig.setPassword( "mysqlRootPassword" );
        ArrayList<MysqlServerConfig> serverList = new ArrayList();
        serverList.add( mysqlServerConfig );
        MysqlClusterConfig mysqlClusterConfig = new MysqlClusterConfig();
        mysqlClusterConfig.setClusterId( 1 );
        mysqlClusterConfig.setServerList( serverList );
        mysqlClusterConfig.calcServerWeight();
        FusionCache.put( MysqlClusterConfig.class, mysqlClusterConfig.getClusterId(), mysqlClusterConfig, true );
        new SingleListTask( 1, new LocalCmdCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> data) {
                for (String line : data) {
                    logger.info( "db:{}", line );
                }
            }

            @Override
            public void onFailure(int errorNo, String message) {
                logger.error( "errorNo[{}]:{}", errorNo, message );
            }
        } ).run( "show databases" );
//        Thread.sleep( 3000 );
//        stop();
    }

    /**
     * 获得mysql session。
     *
     * @param clusterId
     * @return
     */
    public static MySqlSession getMySqlSession(long clusterId, boolean isMaster) {
        MysqlClusterConfig clusterConfig = FusionCache.get( MysqlClusterConfig.class, clusterId );
        if (clusterConfig == null) {
            return null;
        }
        MysqlServerConfig mysqlServerConfig = clusterConfig.fetchServerConfig( isMaster );
        FixedChannelPool channelPool = poolMap.get( mysqlServerConfig );
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
        poolMap = new AbstractChannelPoolMap<>() {
            @Override
            protected FixedChannelPool newPool(MysqlServerConfig mysqlServerConfig) {
                return new FixedChannelPool( bootstrap.remoteAddress( mysqlServerConfig.getHost(), mysqlServerConfig.getPort() ), new MysqlPoolHandler( mysqlServerConfig ), 1000 );
            }
        };
        logger.info( "MySqlClient started!" );
    }

    /**
     * 关闭服务器。
     */
    public static void stop() {
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
        logger.info( "MySqlClient stopped!" );
    }
}
