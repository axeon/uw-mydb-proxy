package uw.mydb.proxy;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ProxySession管理器。
 *
 * @author axeon
 */
public class ProxySessionManager {

    /**
     * key是用户ip端口。
     */
    private static ConcurrentHashMap<String, ProxySession> sessionMap = new ConcurrentHashMap();

    /**
     * 获得在线计数。
     *
     * @return
     */
    public static int getCount() {
        return sessionMap.size();
    }

    /**
     * 获得map实例。
     *
     * @return
     */
    public static ConcurrentHashMap<String, ProxySession> getSessionMap() {
        return sessionMap;
    }

    /**
     * 增加一个session。
     *
     * @param key
     * @param session
     */
    public static void put(String key, ProxySession session) {
        sessionMap.put(key, session);
    }

    /**
     * 移除一个session。
     *
     * @param key
     */
    public static void remove(String key) {
        sessionMap.remove(key);
    }
}
