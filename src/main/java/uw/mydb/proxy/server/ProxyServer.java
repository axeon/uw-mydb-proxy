package uw.mydb.proxy.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.stats.StatsManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
     * 后台调度任务。
     */
    private static ScheduledExecutorService scheduledExecutorService;

    /**
     * 启动服务器
     */
    public static void start() throws InterruptedException {
        // acceptor
        bossGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb_boss-%d" ).build() );
        // worker
        workerGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb_worker-%d" ).build() );
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group( bossGroup, workerGroup ).channel( NioServerSocketChannel.class ).option( ChannelOption.SO_BACKLOG, 100_000 ).childOption( ChannelOption.TCP_NODELAY,
                true ).option( ChannelOption.SO_RCVBUF, 32 * 1024 * 1024 ).childOption( ChannelOption.SO_SNDBUF, 32 * 1024 * 1024 ).option( ChannelOption.ALLOCATOR,
                PooledByteBufAllocator.DEFAULT ).childHandler( new ProxyHandlerFactory() );
        //此处写死了，应该可配置。
        bootstrap.bind( "0.0.0.0", MydbProxyConfigService.getMydbProperties().getProxyPort() ).sync();
        //设置后台调度任务。
        scheduledExecutorService = Executors.newScheduledThreadPool( 1, r -> {
            Thread thread = new Thread( r );
            thread.setName( "mysql-housekeeping-task" );
            thread.setDaemon( true );
            return thread;
        } );
        //每分钟报告一次proxy运行统计
        scheduledExecutorService.scheduleAtFixedRate( () -> {
            try {
                StatsManager.reportProxyRunStats();
            } catch (Throwable e) {
                logger.error( e.getMessage(), e );
            }
        }, 0, 1, TimeUnit.MINUTES );
        //每隔3小时报告一次schema统计信息。
        scheduledExecutorService.scheduleAtFixedRate( () -> {
            try {
                StatsManager.reportSchemaRunStats();
            } catch (Throwable e) {
                logger.error( e.getMessage(), e );
            }
        }, 1, 1, TimeUnit.MINUTES );
        logger.info( "mydb proxy server started!" );
    }

    /**
     * 关闭服务器。
     */
    public static void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
