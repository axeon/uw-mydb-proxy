package uw.mydb.proxy.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

/**
 * 前端 {@link ProxySession} 的进程内管理器，全静态方法。
 * <p>
 * 使用 {@link ConcurrentHashMap} 按 "remoteAddress 字符串"（IP:port）索引活跃会话，
 * 用于在线连接计数、按 IP 汇总连接数（供运行统计上报）。
 * 所有方法线程安全，可由 Netty EventLoop 线程并发调用。
 *
 * @author axeon
 */
public class ProxySessionManager {

    /**
     * 在线会话表，key 为 channel remoteAddress 的 {@code toString()}（形如 "/127.0.0.1:54321"），value 为对应 ProxySession。
     */
    private static final ConcurrentHashMap<String, ProxySession> sessionMap = new ConcurrentHashMap<>();

    /**
     * @return 当前在线会话数量
     */
    public static int getCount() {
        return sessionMap.size();
    }

    /**
     * @return 会话表（直接暴露内部 map，调用方不应做结构性修改）
     */
    public static ConcurrentHashMap<String, ProxySession> getSessionMap() {
        return sessionMap;
    }

    /**
     * 注册一个会话到管理器。
     *
     * @param key     remoteAddress 字符串
     * @param session 会话实例
     */
    public static void put(String key, ProxySession session) {
        sessionMap.put( key, session );
    }

    /**
     * 移除一个会话。
     *
     * @param key remoteAddress 字符串
     */
    public static void remove(String key) {
        sessionMap.remove( key );
    }

    /**
     * 按客户端 IP 聚合当前在线连接数，供 proxy 运行统计上报使用。
     *
     * @return key=客户端 IP，value=该 IP 的连接数
     */
    public static Map<String, Long> getClientConnMap() {
        return ProxySessionManager.getSessionMap().values().stream().map( ProxySession::getClientHost ).collect( Collectors.groupingBy( Function.identity(), counting() ) );
    }

    /**
     * @return 当前连接数（等价于 {@link #getCount()}）
     */
    public static int getConnectionNum() {
        return ProxySessionManager.getCount();
    }
}
