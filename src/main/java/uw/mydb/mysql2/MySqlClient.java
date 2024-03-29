package uw.mydb.mysql2;

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
import uw.mydb.vo.MysqlServerConfig;


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
        MysqlServerConfig config = new MysqlServerConfig();
        config.setHost( "192.168.88.21" );
        config.setPort( 3308 );
        config.setWeight( 1 );
        config.setUser( "root" );
        config.setPass( "mysqlRootPassword" );
        FixedChannelPool pool = poolMap.get( config );
        Future<Channel> future = pool.acquire();
//        future.addListener( new FutureListener<Channel>() {
//            @Override
//            public void operationComplete(Future<Channel> future) throws Exception {
        //给服务端发送数据
//                Channel channel = future.getNow();
        // 连接放回连接池，这里一定记得放回去
//                pool.release(channel);
//            }
//        });
        Thread.sleep( 60_000L );
    }

    /**
     * 启动服务器
     */
    public static void start() {
        eventLoopGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mysql-event-%d" ).build() );
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group( eventLoopGroup ).channel( NioSocketChannel.class )
                .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000 )
                .option( ChannelOption.TCP_NODELAY, true );
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
