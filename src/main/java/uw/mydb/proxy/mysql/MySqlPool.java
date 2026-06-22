package uw.mydb.proxy.mysql;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.common.util.SystemClock;

import java.util.Deque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static uw.mydb.proxy.mysql.MySqlHandler.MYSQL_SESSION;

/**
 * 基于Netty的弹性MySQL后端连接池。
 *
 * <p>职责：
 * <ul>
 *   <li>维护单个 {@link MysqlServerConfig}（一主或一从）对应的后端连接集合，对外提供
 *       {@link #acquire()} / {@link #release(Channel)} 的获取/归还语义；</li>
 *   <li>实现连接上限控制、空闲/忙碌/寿命超时清理、健康检测等池化策略；</li>
 *   <li>连接建立后由 {@link MysqlPoolHandler#channelCreated(Channel)} 在 pipeline 上挂载
 *       {@link MysqlPacketDecoder}、{@link MySqlHandler} 与 {@link MySqlSession}，业务方
 *       通过 channel attr {@link MySqlHandler#MYSQL_SESSION} 拿到会话实例。</li>
 * </ul>
 *
 * <h3>双队列模型</h3>
 * <ul>
 *   <li>{@link #idleDeque}：空闲连接双端队列（LIFO 用法，热复用），等待被 acquire；</li>
 *   <li>{@link #busySet}：在用连接集合，由 acquire 时 add、release 时 remove，
 *       housekeeping 时据此检查 busy 超时。</li>
 * </ul>
 *
 * <h3>线程安全模型</h3>
 * <ul>
 *   <li>idleDeque 使用 {@link ConcurrentLinkedDeque}，busySet 使用
 *       {@code ConcurrentHashMap.newKeySet()}，两者均可被多线程并发读写；</li>
 *   <li>对单个 channel 的实际 healthCheck / release 操作通过 {@code loop.inEventLoop()}
 *       判断后路由到该 channel 绑定的 EventLoop 执行，避免在非归属线程操作 channel；</li>
 *   <li>{@link #acquiredPermits} 作为原子配额计数器（CAS），保证 check-then-act 不会
 *       因竞态突破 {@link #connMax}。</li>
 * </ul>
 *
 * <h3>关键容量/超时参数（均带单位）</h3>
 * <ul>
 *   <li>{@link #connMin}：最小连接数（条），housekeeping 时即使空闲也不会清理到低于此数；</li>
 *   <li>{@link #connMax}：最大连接数（条），新建连接前用 CAS 配额守护，超限即拒；</li>
 *   <li>{@link #connIdleTimeoutMillis}：空闲超时（毫秒），空闲超过此值且 idle 总数 &gt; connMin
 *       时由 housekeeping 关闭，默认 600_000ms（10分钟）；</li>
 *   <li>{@link #connBusyTimeoutMillis}：忙时超时（毫秒），处于 busySet 中超过此值的连接
 *       被强制关闭，避免单条查询长时间占用连接，默认 3600_000ms（1小时）；</li>
 *   <li>{@link #connMaxAgeMillis}：连接最大寿命（毫秒），无论空闲与否超过此值即关闭重建，
 *       默认 36000_000ms（10小时）。</li>
 * </ul>
 *
 * <h3>acquiredPermits 配额计数器</h3>
 * 记录"已成功建连且尚未真正关闭"的连接条数。acquire 时如果池中无空闲连接，需要新建连接前
 * 通过 CAS 自增；connectChannel 失败、closeChannel 真正关闭连接时自减。复用空闲连接不占用
 * 新配额。该计数器保证任意瞬时活跃连接数（idle + busy）不超过 connMax。
 *
 * <h3>housekeeping 清理逻辑</h3>
 * 由 {@link MySqlClient} 的定时调度线程周期性调用（默认 60s/次），分两步扫描：
 * <ol>
 *   <li>扫 idleDeque：超 idleTimeout（且非保底 connMin）或超 maxAge 的连接直接关闭；
 *       其余重新 offer 回队列；</li>
 *   <li>扫 busySet：超 busyTimeout 的连接强制关闭并从 busySet 移除，防止悬挂请求长期占位。</li>
 * </ol>
 *
 * @author axeon
 */
public class MySqlPool implements ChannelPool {
    private static final Logger log = LoggerFactory.getLogger( MySqlPool.class );
    /**
     * channel 上反向指向所属 pool 的 AttributeKey，release 时据此校验 channel 来源合法性。
     */
    private static final AttributeKey<MySqlPool> POOL_KEY = AttributeKey.newInstance( "uw.mydb.mysql.pool.ElasticChannelPool" );

    /**
     * 空闲连接队列。被 release 归还且通过健康检测的连接 offer 至队尾，acquire 时从队首 poll。
     * 线程安全：使用 {@link ConcurrentLinkedDeque}，可被 EventLoop 与 housekeeping 线程并发访问。
     */
    private final Deque<Channel> idleDeque = new ConcurrentLinkedDeque<>();

    /**
     * 在用连接集合。acquire 成功后加入，release 时移除；housekeeping 据此检查 busy 超时。
     * 线程安全：{@link ConcurrentHashMap#newKeySet()} 提供的并发 Set。
     */
    private final Set<Channel> busySet = ConcurrentHashMap.newKeySet();

    /**
     * 连接池事件处理器（实际类型为 {@link MysqlPoolHandler}），在 channelCreated/Released/Acquired 回调中
     * 装配 pipeline 并打印日志。
     */
    private final ChannelPoolHandler handler;

    /**
     * channel 健康检测器，固定为 {@link ChannelHealthChecker#ACTIVE}（仅检测 channel 是否 active）。
     */
    private final ChannelHealthChecker healthCheck;

    /**
     * 用于新建连接的 Netty {@link Bootstrap}，构造时 clone 自入参并替换 handler 为 channelCreated 桥接。
     */
    private final Bootstrap bootstrap;

    /**
     * release 时是否先做健康检测。固定为 true，确保归还到 idleDeque 的连接都是健康的。
     */
    private final boolean releaseHealthCheck;

    /**
     * 最小连接数（条）。housekeeping 清理空闲连接时不会让 idleDeque 跌破此值。默认 1。
     */
    private int connMin = 1;

    /**
     * 最大连接数（条）。{@link #acquiredPermits} 通过 CAS 守护此上限，达到即拒绝新建。默认 1000。
     */
    private int connMax = 1000;

    /**
     * 已占用的连接配额计数器（原子）。
     * <p>记录"建连成功且尚未真正 close"的连接条数，用于保证 connMax 上限的线程安全，
     * 避免 check-then-act 竞态导致超出 connMax。复用空闲连接不占用新配额。
     */
    private final java.util.concurrent.atomic.AtomicInteger acquiredPermits = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 连接空闲超时（毫秒）。空闲连接超过此值且 idleDeque.size() &gt; connMin 时，由 housekeeping 关闭。
     * 默认 600_000ms（10分钟）。
     */
    private long connIdleTimeoutMillis = 600_000L;

    /**
     * 连接忙时超时（毫秒）。处于 busySet 中超过此值的连接被 housekeeping 强制关闭，避免悬挂请求长期占位。
     * 默认 3600_000ms（1小时）。
     */
    private long connBusyTimeoutMillis = 3600_000L;

    /**
     * 连接最大寿命（毫秒）。无论空闲与否，存活超过此值的连接由 housekeeping 关闭重建。
     * 默认 36000_000ms（10小时）。
     */
    private long connMaxAgeMillis = 36000_000L;

    /**
     * 创建一个连接池实例。
     * <p>构造时会 clone 入参 bootstrap 并替换 handler 为内部 {@link ChannelInitializer}，确保
     * 新建 channel 时通过 {@link ChannelPoolHandler#channelCreated(Channel)} 通知到 {@link #handler}。
     * 所有容量/超时参数仅当入参 &gt; 0 时才覆盖默认值。
     *
     * @param bootstrap             用于建立后端 MySQL 连接的 {@link Bootstrap}（会被 clone，不会修改入参）
     * @param handler               池事件回调处理器（通常为 {@link MysqlPoolHandler}）
     * @param connMin               最小连接数（条），&gt;0 时生效
     * @param connMax               最大连接数（条），&gt;0 时生效
     * @param connIdleTimeoutMillis 空闲超时毫秒数，&gt;0 时生效
     * @param connBusyTimeoutMillis 忙时超时毫秒数，&gt;0 时生效
     * @param connMaxAgeMillis      连接最大寿命毫秒数，&gt;0 时生效
     * @throws NullPointerException 当 bootstrap 或 handler 为 null 时
     */
    public MySqlPool(Bootstrap bootstrap, ChannelPoolHandler handler, int connMin, int connMax, long connIdleTimeoutMillis, long connBusyTimeoutMillis, long connMaxAgeMillis) {
        this.handler = checkNotNull( handler, "handler" );
        this.healthCheck = ChannelHealthChecker.ACTIVE;
        this.releaseHealthCheck = true;
        // Clone the original Bootstrap as we want to set our own handler
        this.bootstrap = checkNotNull( bootstrap, "bootstrap" ).clone();
        this.bootstrap.handler( new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                assert ch.eventLoop().inEventLoop();
                handler.channelCreated( ch );
            }
        } );
        if (connMin > 0) {
            this.connMin = connMin;
        }
        if (connMax > 0) {
            this.connMax = connMax;
        }
        if (connIdleTimeoutMillis > 0) {
            this.connIdleTimeoutMillis = connIdleTimeoutMillis;
        }
        if (connBusyTimeoutMillis > 0) {
            this.connBusyTimeoutMillis = connBusyTimeoutMillis;
        }
        if (connMaxAgeMillis > 0) {
            this.connMaxAgeMillis = connMaxAgeMillis;
        }
    }

    /**
     * @return 最小连接数（条）
     */
    public int getConnMin() {
        return connMin;
    }

    /**
     * @return 最大连接数（条）
     */
    public int getConnMax() {
        return connMax;
    }

    /**
     * @return 空闲超时（毫秒）
     */
    public long getConnIdleTimeoutMillis() {
        return connIdleTimeoutMillis;
    }

    /**
     * @return 忙时超时（毫秒）
     */
    public long getConnBusyTimeoutMillis() {
        return connBusyTimeoutMillis;
    }

    /**
     * @return 连接最大寿命（毫秒）
     */
    public long getConnMaxAgeMillis() {
        return connMaxAgeMillis;
    }

    /**
     * @return 当前空闲连接数（idleDeque 大小，条）
     */
    public int getIdleConnNum() {
        return idleDeque.size();
    }

    /**
     * @return 当前在用连接数（busySet 大小，条）
     */
    public int getBusyConnNum() {
        return busySet.size();
    }

    /**
     * 异步获取一个 channel，使用 EventLoopGroup 中下一个 EventLoop 创建 promise。
     *
     * @return acquire 操作的 Future，完成后携带 channel 或失败原因
     */
    @Override
    public final Future<Channel> acquire() {
        return acquire( bootstrap.config().group().next().<Channel>newPromise() );
    }

    /**
     * 异步获取一个 channel。
     *
     * @param promise 调用方提供的 promise，由本方法完成
     * @return 入参 promise 本身
     * @throws NullPointerException promise 为 null
     */
    @Override
    public Future<Channel> acquire(final Promise<Channel> promise) {
        return acquireHealthyFromPoolOrNew( checkNotNull( promise, "promise" ) );
    }

    /**
     * 归还一个 channel。此处先从 busySet 移除（避免健康检测不通过时漏移除），再委托给
     * {@link #release(Channel, Promise)}。
     *
     * @param channel 待归还的 channel，必须来自本 pool
     * @return release 操作的 Future
     */
    @Override
    public final Future<Void> release(Channel channel) {
        //必须在这里移除掉，避免因为健康检测无法正确移除。
        busySet.remove( channel );
        return release( channel, channel.eventLoop().<Void>newPromise() );
    }

    @Override
    public Future<Void> release(final Channel channel, final Promise<Void> promise) {
        try {
            checkNotNull( channel, "channel" );
            checkNotNull( promise, "promise" );
            EventLoop loop = channel.eventLoop();
            if (loop.inEventLoop()) {
                doReleaseChannel( channel, promise );
            } else {
                loop.execute( new Runnable() {
                    @Override
                    public void run() {
                        doReleaseChannel( channel, promise );
                    }
                } );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
        return promise;
    }

    /**
     * 同步关闭整个连接池：循环 poll 出所有空闲 channel 并 await close。
     * 注意：仅清理 idleDeque，不主动关闭 busySet 中的连接（这些将由在途请求 release 时处理）。
     */
    @Override
    public void close() {
        for (; ; ) {
            Channel channel = pollChannel();
            if (channel == null) {
                break;
            }
            // Just ignore any errors that are reported back from close().
            channel.close().awaitUninterruptibly();
        }
    }

    /**
     * 异步关闭连接池，避免在 EventLoop 线程中阻塞调用 close()。
     *
     * @return close 任务完成 Future
     */
    public Future<Void> closeAsync() {
        // Execute close asynchronously in case this is being invoked on an eventloop to avoid blocking
        return GlobalEventExecutor.INSTANCE.submit( new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                close();
                return null;
            }
        } );
    }

    /**
     * 连接池维护任务，由 {@link MySqlClient} 的 housekeeping 定时调度线程周期性调用。
     *
     * <p>清理策略分两段：
     * <ol>
     *   <li>idleDeque：对每个空闲 channel，若 session 缺失、或超过 connMaxAgeMillis 寿命、
     *       或（空闲数 &gt; connMin 且超过 connIdleTimeoutMillis 空闲），则 closeChannel 关闭；
     *       否则重新 offer 回 idleDeque。注意空闲数不超过 connMin 时跳过 idle 超时检查以保底。</li>
     *   <li>busySet：对每个 busy channel，若 session 缺失或超过 connBusyTimeoutMillis 忙时，
     *       closeChannel 关闭并从 busySet 移除，防止悬挂请求长期占位。</li>
     * </ol>
     * closeChannel 内部会归还 {@link #acquiredPermits} 配额，使后续 acquire 能新建连接补位。
     */
    protected void housekeeping() {
        //先检查idleDeque。
        int idleLoop = idleDeque.size();
        for (int i = 0; i < idleLoop; i++) {
            Channel channel = idleDeque.pollFirst();
            if (channel == null) {
                break;
            }
            //如果超出了最小连接数数值，则进行检查。
            MySqlSession session = channel.attr( MYSQL_SESSION ).get();
            boolean readyClose = false;
            if (session != null) {
                long now = SystemClock.now();
                if (idleDeque.size() > connMin) {
                    if ((now - session.getLastRequestTime()) > connIdleTimeoutMillis) {
                        if (log.isDebugEnabled()) {
                            log.debug( "Channel[{}] will be closed because reach idle timeout[{}]!", channel, connIdleTimeoutMillis );
                        }
                        //准备释放链接吧。
                        readyClose = true;
                    }
                }
                if ((now - session.getCreateTime()) > connMaxAgeMillis) {
                    if (log.isDebugEnabled()) {
                        log.debug( "Channel[{}] will be closed because reach max age[{}]!", channel, connMaxAgeMillis );
                    }
                    //准备释放链接吧。
                    readyClose = true;
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug( "Channel[{}] will be closed because lost session info!", channel );
                }
                //异常channel，也准备关闭吧。
                readyClose = true;
            }
            if (readyClose) {
                try {
                    closeChannel( channel );
                } catch (Exception e) {
                    log.error( e.getMessage(), e );
                }
            } else {
                idleDeque.offer( channel );
            }
        }
        //再检查busySet。
        Iterator<Channel> busyIterator = busySet.iterator();
        while (busyIterator.hasNext()) {
            Channel channel = busyIterator.next();
            MySqlSession session = channel.attr( MYSQL_SESSION ).get();
            boolean readyClose = false;
            if (session != null) {
                long now = SystemClock.now();
                if ((now - session.getLastRequestTime()) > connBusyTimeoutMillis) {
                    if (log.isDebugEnabled()) {
                        log.debug( "Channel[{}] will be closed because reach busy timeout[{}]!", channel, connBusyTimeoutMillis );
                    }
                    //准备释放链接吧。
                    readyClose = true;
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug( "Channel[{}] will be closed because lost session info!", channel );
                }
                //异常channel，也准备关闭吧。
                readyClose = true;
            }
            if (readyClose) {
                try {
                    closeChannel( channel );
                } catch (Exception e) {
                    log.error( e.getMessage(), e );
                }
                busyIterator.remove();
            }
        }
    }

    /**
     * Returns the {@link Bootstrap} this pool will use to open new connections.
     *
     * @return the {@link Bootstrap} this pool will use to open new connections
     */
    protected Bootstrap bootstrap() {
        return bootstrap;
    }

    /**
     * Returns the {@link ChannelPoolHandler} that will be notified for the different pool actions.
     *
     * @return the {@link ChannelPoolHandler} that will be notified for the different pool actions
     */
    protected ChannelPoolHandler handler() {
        return handler;
    }

    /**
     * Returns the {@link ChannelHealthChecker} that will be used to check if a {@link Channel} is healthy.
     *
     * @return the {@link ChannelHealthChecker} that will be used to check if a {@link Channel} is healthy
     */
    protected ChannelHealthChecker healthChecker() {
        return healthCheck;
    }

    /**
     * Indicates whether this pool will check the health of channels before offering them back into the pool.
     *
     * @return {@code true} if this pool will check the health of channels before offering them back into the pool, or
     * {@code false} if channel health is only checked at acquisition time
     */
    protected boolean releaseHealthCheck() {
        return releaseHealthCheck;
    }

    /**
     * Bootstrap a new {@link Channel}. The default implementation uses {@link Bootstrap#connect()}, sub-classes may
     * override this.
     * <p>
     * The {@link Bootstrap} that is passed in here is cloned via {@link Bootstrap#clone()}, so it is safe to modify.
     */
    protected ChannelFuture connectChannel(Bootstrap bs) {
        return bs.connect();
    }

    /**
     * Poll a {@link Channel} out of the internal storage to reuse it. This will return {@code null} if no
     * {@link Channel} is ready to be reused.
     * <p>
     * Sub-classes may override {@link #pollChannel()} and {@link #offerChannel(Channel)}. Be aware that
     * implementations of these methods needs to be thread-safe!
     */
    protected Channel pollChannel() {
        return idleDeque.pollFirst();
    }

    /**
     * Offer a {@link Channel} back to the internal storage. This will return {@code true} if the {@link Channel}
     * could be added, {@code false} otherwise.
     * <p>
     * Sub-classes may override {@link #pollChannel()} and {@link #offerChannel(Channel)}. Be aware that
     * implementations of these methods needs to be thread-safe!
     */
    protected boolean offerChannel(Channel channel) {
        return idleDeque.offer( channel );
    }

    /**
     * Tries to retrieve healthy channel from the pool if any or creates a new channel otherwise.
     *
     * @param promise the promise to provide acquire result.
     * @return future for acquiring a channel.
     */
    private Future<Channel> acquireHealthyFromPoolOrNew(final Promise<Channel> promise) {
        try {
            final Channel ch = pollChannel();
            if (ch != null) {
                //复用idle连接，无需占用新配额。
                EventLoop loop = ch.eventLoop();
                if (loop.inEventLoop()) {
                    doHealthCheck( ch, promise );
                } else {
                    loop.execute( new Runnable() {
                        @Override
                        public void run() {
                            doHealthCheck( ch, promise );
                        }
                    } );
                }
                return promise;
            }
            //无idle连接，需新建：用CAS原子占用配额，保证不超connMax。
            for (;;) {
                int current = acquiredPermits.get();
                if (current >= connMax) {
                    //达到上限，放入acquireQueue等待release时唤醒（由父类scheduleAcquireLink机制处理）。
                    promise.tryFailure( new IllegalStateException( "Connection pool exhausted, connMax=" + connMax ) );
                    return promise;
                }
                if (acquiredPermits.compareAndSet( current, current + 1 )) {
                    break;
                }
            }
            //已成功占用配额，开始新建连接。
            try {
                Bootstrap bs = bootstrap.clone();
                bs.attr( POOL_KEY, this );
                ChannelFuture f = connectChannel( bs );
                if (f.isDone()) {
                    notifyConnect( f, promise );
                } else {
                    f.addListener( new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            notifyConnect( future, promise );
                        }
                    } );
                }
            } catch (Throwable cause) {
                //新建连接异常，归还配额。
                acquiredPermits.decrementAndGet();
                closeAndFail( null, cause, promise );
            }
        } catch (Throwable cause) {
            promise.tryFailure( cause );
        }
        return promise;
    }

    /**
     * 处理 {@link #connectChannel(Bootstrap)} 的结果。
     * 成功则把 channel 加入 busySet、回调 channelAcquired 并完成 promise（若 promise 已被取消则
     * 立即 release 该 channel）；失败则归还 {@link #acquiredPermits} 配额并失败 promise，
     * 避免因连接失败导致 permits 泄漏使后续无法新建连接。
     *
     * @param future connectChannel 的结果 Future
     * @param promise acquire 操作的 promise
     */
    private void notifyConnect(ChannelFuture future, Promise<Channel> promise) {
        Channel channel = null;
        try {
            if (future.isSuccess()) {
                channel = future.channel();
                //此处放入busySet
                busySet.add( channel );
                handler.channelAcquired( channel );
                if (!promise.trySuccess( channel )) {
                    // Promise was completed in the meantime (like cancelled), just release the channel again
                    release( channel );
                }
            } else {
                //连接创建失败，归还已占用的配额，避免permits泄漏导致后续无法新建连接。
                acquiredPermits.decrementAndGet();
                promise.tryFailure( future.cause() );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    /**
     * 复用 idle channel 前的健康检测入口。调用此方法前必须确认当前线程在该 channel 的 EventLoop 中。
     * 检测结果交给 {@link #notifyHealthCheck(Future, Channel, Promise)} 处理。
     *
     * @param channel 待检测的 idle channel
     * @param promise acquire 操作的 promise
     */
    private void doHealthCheck(final Channel channel, final Promise<Channel> promise) {
        try {
            assert channel.eventLoop().inEventLoop();
            Future<Boolean> f = healthCheck.isHealthy( channel );
            if (f.isDone()) {
                notifyHealthCheck( f, channel, promise );
            } else {
                f.addListener( new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) {
                        notifyHealthCheck( future, channel, promise );
                    }
                } );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    /**
     * 处理健康检测结果（acquire 路径）。
     * 健康：绑定 POOL_KEY 到本 pool、加入 busySet、回调 channelAcquired 并成功 promise；
     * 不健康：closeChannel 关闭并以原 promise 递归调用 {@link #acquireHealthyFromPoolOrNew} 重新尝试。
     */
    private void notifyHealthCheck(Future<Boolean> future, Channel channel, Promise<Channel> promise) {
        try {
            assert channel.eventLoop().inEventLoop();
            if (future.isSuccess() && future.getNow()) {
                channel.attr( POOL_KEY ).set( this );
                //此处放入busySet
                busySet.add( channel );
                handler.channelAcquired( channel );
                promise.setSuccess( channel );
            } else {
                closeChannel( channel );
                acquireHealthyFromPoolOrNew( promise );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    /**
     * 归还 channel 的实际执行体，必须在 channel 归属 EventLoop 中调用。
     * 流程：校验 POOL_KEY 是否来自本 pool（否则失败关闭），通过则按 {@link #releaseHealthCheck}
     * 决定走健康检测路径或直接 releaseAndOffer。
     */
    private void doReleaseChannel(Channel channel, Promise<Void> promise) {
        try {
            assert channel.eventLoop().inEventLoop();
            // Remove the POOL_KEY attribute from the Channel and check if it was acquired from this pool, if not fail.
            if (channel.attr( POOL_KEY ).getAndSet( null ) != this) {
                closeAndFail( channel,
                        // Better include a stacktrace here as this is an user error.
                        new IllegalArgumentException( "Channel " + channel + " was not acquired from this ChannelPool" ), promise );
            } else {
                if (releaseHealthCheck) {
                    doHealthCheckOnRelease( channel, promise );
                } else {
                    releaseAndOffer( channel, promise );
                }
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    /**
     * release 路径的健康检测入口。同步/异步完成后统一交给
     * {@link #releaseAndOfferIfHealthy(Channel, Promise, Future)} 处理。
     */
    private void doHealthCheckOnRelease(final Channel channel, final Promise<Void> promise) throws Exception {
        final Future<Boolean> f = healthCheck.isHealthy( channel );
        if (f.isDone()) {
            releaseAndOfferIfHealthy( channel, promise, f );
        } else {
            f.addListener( new FutureListener<Boolean>() {
                @Override
                public void operationComplete(Future<Boolean> future) throws Exception {
                    releaseAndOfferIfHealthy( channel, promise, f );
                }
            } );
        }
    }

    /**
     * Adds the channel back to the pool only if the channel is healthy.
     *
     * @param channel the channel to put back to the pool
     * @param promise offer operation promise.
     * @param future  the future that contains information fif channel is healthy or not.
     * @throws Exception in case when failed to notify handler about release operation.
     */
    private void releaseAndOfferIfHealthy(Channel channel, Promise<Void> promise, Future<Boolean> future) {
        try {
            if (future.getNow()) { //channel turns out to be healthy, offering and releasing it.
                releaseAndOffer( channel, promise );
            } else { //channel not healthy，必须关闭连接并归还配额，否则channel泄漏且permits停滞。
                handler.channelReleased( channel );
                closeChannel( channel );
                promise.setSuccess( null );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    /**
     * 将 channel offer 回 idleDeque 并回调 channelReleased、完成 promise。
     * offer 失败（理论上 ConcurrentLinkedDeque 不会拒绝）则关闭 channel 并以
     * {@link ChannelPoolFullException} 失败 promise。
     */
    private void releaseAndOffer(Channel channel, Promise<Void> promise) throws Exception {
        if (offerChannel( channel )) {
            //此处释放计数。
            handler.channelReleased( channel );
            promise.setSuccess( null );
        } else {
            closeAndFail( channel, new ChannelPoolFullException(), promise );
        }
    }

    /**
     * 真正关闭一个 channel：清除 POOL_KEY、关闭 channel，并归还 {@link #acquiredPermits} 配额，
     * 使后续 acquireHealthyFromPoolOrNew 能新建连接补位。acquiredPermits 仅在真正 close 时归还，
     * 避免 idle/busy 之间流转时误减。
     *
     * @param channel 待关闭的 channel
     */
    private void closeChannel(Channel channel) throws Exception {
        channel.attr( POOL_KEY ).getAndSet( null );
        channel.close();
        //连接真正关闭，归还配额，使acquireHealthyFromPoolOrNew能新建连接补充。
        acquiredPermits.decrementAndGet();
    }

    /**
     * 关闭 channel（如有）并将 cause 设置到 promise 的失败结果。
     * closeChannel 抛出的异常会优先覆盖原 cause 写入 promise。
     */
    private void closeAndFail(Channel channel, Throwable cause, Promise<?> promise) {
        if (channel != null) {
            try {
                closeChannel( channel );
            } catch (Throwable t) {
                promise.tryFailure( t );
            }
        }
        promise.tryFailure( cause );
    }

    private static final class ChannelPoolFullException extends IllegalStateException {

        private ChannelPoolFullException() {
            super( "ChannelPool full" );
        }

        // Suppress a warning since the method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}