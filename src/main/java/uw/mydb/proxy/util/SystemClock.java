package uw.mydb.proxy.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 弱精度的计时器，在超高并发下可以提高性能。
 *
 * @author axeon
 */
public class SystemClock {

    /**
     * 更新时间。
     */
    private static final long PERIOD = 11L;

    /**
     * 当前时间戳。
     */
    private static final AtomicLong NOW = new AtomicLong( System.currentTimeMillis() );

    /**
     * 默认构造器。
     */
    private SystemClock() {

        ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor( 1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread( runnable, "System Clock" );
                thread.setDaemon( true );
                return thread;
            }
        } );

        scheduler.scheduleAtFixedRate( new Runnable() {
            @Override
            public void run() {
                NOW.set( System.currentTimeMillis() );
            }
        }, PERIOD, PERIOD, TimeUnit.MILLISECONDS );
    }

    public static long now() {
        return NOW.get();
    }

    public static long elapsedMillis(long startTime) {
        return now() - startTime;
    }

    public static long elapsedMillis(final long startTime, final long endTime) {
        return endTime - startTime;
    }

    public static long plusMillis(final long time, final long millis) {
        return time + millis;
    }


}