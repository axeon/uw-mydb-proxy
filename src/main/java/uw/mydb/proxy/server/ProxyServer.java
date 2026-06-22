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
 * 前端代理服务器，基于 Netty 实现 MySQL 协议服务端，对外暴露为一个 MySQL Server，
 * 接收 MySQL 客户端（JDBC 驱动 / mysql cli 等）的连接，经 {@link ProxyDataHandler} 解析命令后路由到后端真实分库分表。
 * <p>
 * 线程模型：
 * <ul>
 *   <li>{@link #bossGroup}：acceptor 线程组，负责接收客户端 TCP 连接（NIO）。</li>
 *   <li>{@link #workerGroup}：reactor 线程组，负责已接入连接的读写与协议解码；同一连接的全部 IO 事件均由其中单个 EventLoop 线程串行处理，因此 IO 线程禁止执行阻塞操作（见 {@link ProxySession} 中的异步派发设计）。</li>
 *   <li>{@link #scheduledExecutorService}：daemon 定时线程池（size=2），周期性向 mydb-center 上报代理运行统计与 schema 统计。</li>
 * </ul>
 * 生命周期：{@link #start()} 在 Spring 容器启动后由 {@code UwMydbProxyApplication} 调用一次；
 * {@link #stop()} 由 {@code MydbProxySpringAutoConfiguration} 在 {@code @PreDestroy} 时调用。
 * <p>
 * 关键 socket 选项：
 * <ul>
 *   <li>{@code SO_BACKLOG=100000}：接受队列容量，应对突发短连接。</li>
 *   <li>{@code TCP_NODELAY=true}：禁用 Nagle，降低小包延迟。</li>
 *   <li>{@code SO_KEEPALIVE=true}：开启 TCP 保活。</li>
 *   <li>收发缓冲各 32MB，使用 {@link PooledByteBufAllocator} 降低分配开销。</li>
 * </ul>
 *
 * @author axeon
 */
public class ProxyServer {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( ProxyServer.class );


    /**
     * acceptor 线程组（接收 TCP 连接）。启动前为 null，{@link #start()} 后非空，{@link #stop()} 后关闭。
     */
    private static EventLoopGroup bossGroup = null;

    /**
     * reactor 线程组（处理已接入连接的 IO 与协议解码）。启动前为 null，{@link #start()} 后非空，{@link #stop()} 后关闭。
     */
    private static EventLoopGroup workerGroup = null;

    /**
     * 后台定时调度线程池（daemon，size=2），用于周期上报 proxy/schema 运行统计。{@link #stop()} 时 {@code shutdownNow}。
     */
    private static ScheduledExecutorService scheduledExecutorService;

    /**
     * 启动代理服务器。绑定 {@code uw.mydb.proxy.proxyHost:proxyPort}（host 为空时监听 0.0.0.0），
     * 注册 {@link ProxyHandlerFactory} 作为子 channel 初始化器，并启动周期统计上报任务。
     * <p>
     * 该方法在 Spring 容器启动完成后由应用入口调用一次；重复调用会重复绑定端口导致异常。
     *
     * @throws InterruptedException 当 {@code bind().sync()} 被中断时抛出
     */
    public static void start() throws InterruptedException {
        // acceptor
        bossGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb-boss-%d" ).build() );
        // worker
        workerGroup = new NioEventLoopGroup( 0, new ThreadFactoryBuilder().setNameFormat( "mydb-worker-%d" ).build() );
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group( bossGroup, workerGroup ).channel( NioServerSocketChannel.class ).option( ChannelOption.SO_BACKLOG, 100_000 ).childOption( ChannelOption.TCP_NODELAY,
                true ).childOption( ChannelOption.SO_KEEPALIVE, true ).option( ChannelOption.SO_RCVBUF, 32 * 1024 * 1024 ).childOption( ChannelOption.SO_SNDBUF, 32 * 1024 * 1024 ).option( ChannelOption.ALLOCATOR,
                PooledByteBufAllocator.DEFAULT ).childHandler( new ProxyHandlerFactory() );
        String proxyHost = MydbProxyConfigService.getMydbProperties().getProxyHost();
        if (proxyHost == null || proxyHost.isEmpty()) {
            proxyHost = "0.0.0.0";
        }
        bootstrap.bind( proxyHost, MydbProxyConfigService.getMydbProperties().getProxyPort() ).sync();
        //设置后台调度任务。
        scheduledExecutorService = Executors.newScheduledThreadPool( 2, r -> {
            Thread thread = new Thread( r );
            thread.setName( "proxy-report-task" );
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
        //每隔1小时报告一次schema统计信息。
        scheduledExecutorService.scheduleAtFixedRate( () -> {
            try {
                StatsManager.reportSchemaRunStats();
            } catch (Throwable e) {
                logger.error( e.getMessage(), e );
            }
        }, 1, 1, TimeUnit.HOURS );
        logger.info( "mydb proxy server started!" );
    }

    /**
     * 关闭代理服务器，释放全部资源：停止统计上报任务、优雅关闭 acceptor / reactor 线程组。
     * 幂等：对 null 引用做了保护，可重复调用。
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
