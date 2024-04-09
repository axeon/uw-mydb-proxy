package uw.mydb.mysql.pool;

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

import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * 一个弹性的channel pool。
 * 可以提供针对最小连接数，最大连接数，空闲时间，最忙时间，最大寿命等条件限制。
 */
public class ElasticChannelPool implements ChannelPool {
    private static final AttributeKey<ElasticChannelPool> POOL_KEY = AttributeKey.newInstance( "uw.mydb.mysql.pool.ElasticChannelPool" );

    /**
     * 连接池。
     */
    private final Deque<Channel> deque = new ConcurrentLinkedDeque();

    /**
     * ChannelPoolHandler
     */
    private final ChannelPoolHandler handler;

    /**
     * 健康检测。
     */
    private final ChannelHealthChecker healthCheck;

    /**
     * bootstrap。
     */
    private final Bootstrap bootstrap;

    /**
     * 释放链接时是否检测健康。
     */
    private final boolean releaseHealthCheck;

    /**
     * 是否使用LRU算法获取连接。
     */
    private final boolean lastRecentUsed;

    /**
     * 最小连接数
     */
    private int connMin = 1;

    /**
     * 最大连接数
     */
    private int connMax = 1000;

    /**
     * 连接闲时超时秒数.
     */
    private int connIdleTimeout = 180;

    /**
     * 连接忙时超时秒数.
     */
    private int connBusyTimeout = 180;

    /**
     * 连接最大寿命秒数.
     */
    private int connMaxAge = 1800;

    /**
     * 创建一个实例。
     *
     * @param bootstrap          the {@link Bootstrap} that is used for connections
     * @param handler            the {@link ChannelPoolHandler} that will be notified for the different pool actions
     * @param healthCheck        the {@link ChannelHealthChecker} that will be used to check if a {@link Channel} is
     *                           still healthy when obtain from the {@link ChannelPool}
     * @param releaseHealthCheck will check channel health before offering back if this parameter set to {@code true};
     *                           otherwise, channel health is only checked at acquisition time
     * @param lastRecentUsed     {@code true} {@link Channel} selection will be LIFO, if {@code false} FIFO.
     */
    public ElasticChannelPool(ChannelPoolHandler handler, ChannelHealthChecker healthCheck, Bootstrap bootstrap, boolean releaseHealthCheck, boolean lastRecentUsed, boolean lastRecentUsed1, int connMin, int connMax, int connIdleTimeout, int connBusyTimeout, int connMaxAge) {
        this.handler = checkNotNull( handler, "handler" );
        this.healthCheck = checkNotNull( healthCheck, "healthCheck" );
        this.releaseHealthCheck = releaseHealthCheck;
        // Clone the original Bootstrap as we want to set our own handler
        this.bootstrap = checkNotNull( bootstrap, "bootstrap" ).clone();
        this.lastRecentUsed = lastRecentUsed1;
        this.bootstrap.handler( new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                assert ch.eventLoop().inEventLoop();
                handler.channelCreated( ch );
            }
        } );
        this.connMin = connMin;
        this.connMax = connMax;
        this.connIdleTimeout = connIdleTimeout;
        this.connBusyTimeout = connBusyTimeout;
        this.connMaxAge = connMaxAge;
    }

    @Override
    public final Future<Channel> acquire() {
        return acquire( bootstrap.config().group().next().<Channel>newPromise() );
    }

    @Override
    public Future<Channel> acquire(final Promise<Channel> promise) {
        return acquireHealthyFromPoolOrNew( checkNotNull( promise, "promise" ) );
    }

    @Override
    public final Future<Void> release(Channel channel) {
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
     * Closes the pool in an async manner.
     *
     * @return Future which represents completion of the close task
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
        return lastRecentUsed ? deque.pollLast() : deque.pollFirst();
    }

    /**
     * Offer a {@link Channel} back to the internal storage. This will return {@code true} if the {@link Channel}
     * could be added, {@code false} otherwise.
     * <p>
     * Sub-classes may override {@link #pollChannel()} and {@link #offerChannel(Channel)}. Be aware that
     * implementations of these methods needs to be thread-safe!
     */
    protected boolean offerChannel(Channel channel) {
        return deque.offer( channel );
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
            if (ch == null) {
                // No Channel left in the pool bootstrap a new Channel
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
            } else {
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
            }
        } catch (Throwable cause) {
            promise.tryFailure( cause );
        }
        return promise;
    }

    private void notifyConnect(ChannelFuture future, Promise<Channel> promise) {
        Channel channel = null;
        try {
            if (future.isSuccess()) {
                channel = future.channel();
                handler.channelAcquired( channel );
                if (!promise.trySuccess( channel )) {
                    // Promise was completed in the meantime (like cancelled), just release the channel again
                    release( channel );
                }
            } else {
                promise.tryFailure( future.cause() );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

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

    private void notifyHealthCheck(Future<Boolean> future, Channel channel, Promise<Channel> promise) {
        try {
            assert channel.eventLoop().inEventLoop();
            if (future.isSuccess() && future.getNow()) {
                channel.attr( POOL_KEY ).set( this );
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
            } else { //channel not healthy, just releasing it.
                handler.channelReleased( channel );
                promise.setSuccess( null );
            }
        } catch (Throwable cause) {
            closeAndFail( channel, cause, promise );
        }
    }

    private void releaseAndOffer(Channel channel, Promise<Void> promise) throws Exception {
        if (offerChannel( channel )) {
            handler.channelReleased( channel );
            promise.setSuccess( null );
        } else {
            closeAndFail( channel, new ChannelPoolFullException(), promise );
        }
    }

    private void closeChannel(Channel channel) throws Exception {
        channel.attr( POOL_KEY ).getAndSet( null );
        channel.close();
    }

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