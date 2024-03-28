package uw.mydb.mysql2;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.LoggerFactory;

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
    public static ChannelPoolMap<String, FixedChannelPool> poolMap;
    /**
     * acceptor线程。
     */
    private static EventLoopGroup eventLoopGroup = null;

    /**
     * 启动服务器
     */
    public static void start() throws InterruptedException {
        eventLoopGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mysql-event-%d" ).build() );
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group( eventLoopGroup ).channel( NioSocketChannel.class ).option( ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000 ).option( ChannelOption.ALLOCATOR,
                PooledByteBufAllocator.DEFAULT ).option( ChannelOption.TCP_NODELAY, false ).handler( new MySqlHandlerFactory() );
        poolMap = new AbstractChannelPoolMap<String, FixedChannelPool>() {
            @Override
            protected FixedChannelPool newPool(String s) {
                return new FixedChannelPool( bootstrap, new MysqlPoolHandler(), 1000 );
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
