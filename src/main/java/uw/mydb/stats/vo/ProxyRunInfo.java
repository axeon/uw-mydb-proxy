package uw.mydb.stats.vo;

import uw.mydb.proxy.ProxySession;
import uw.mydb.proxy.ProxySessionManager;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

/**
 * MyDb运行信息。
 *
 * @author axeon
 */
public class ProxyRunInfo {

    /**
     * 获得当前连接数。
     *
     * @return
     */
    public int getTotalConnections() {
        return ProxySessionManager.getCount();
    }

    /**
     * 获得链接映射表。
     *
     * @return
     */
    public Map<String, Long> getConnectionMap() {
        return ProxySessionManager.getSessionMap().values().stream().map( ProxySession::getClientHost ).collect(Collectors.groupingBy(Function.identity(), counting()));
    }



}
