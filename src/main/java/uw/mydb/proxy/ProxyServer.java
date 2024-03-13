package uw.mydb.proxy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.LoggerFactory;

/**
 * 代理服务器。
 *
 * @author axeon
 */
public class ProxyServer {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( ProxyServer.class );


    /**
     * acceptor线程。
     */
    private static EventLoopGroup bossGroup = null;

    /**
     * reactor线程。
     */
    private static EventLoopGroup workerGroup = null;

    /**
     * 启动服务器
     */
    public static void start() throws InterruptedException {
        // acceptor
        bossGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb_boss-%d" ).build() );
        // worker
        workerGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb_worker-%d" ).build() );
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group( bossGroup, workerGroup ).channel( NioServerSocketChannel.class )
                .option( ChannelOption.SO_BACKLOG, 1024 )
                .childOption( ChannelOption.TCP_NODELAY, true )
                .option( ChannelOption.SO_RCVBUF, 32 * 1024 )
                .childOption( ChannelOption.SO_SNDBUF, 32 * 1024 )
                .option( ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT )
                .childHandler( new ProxyHandlerFactory() );
        //此处写死了，应该可配置。
        bootstrap.bind( "0.0.0.0", 3300 ).sync();
        logger.info( "mydb proxy server started!" );
    }

    /**
     * 关闭服务器。
     */
    public static void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
