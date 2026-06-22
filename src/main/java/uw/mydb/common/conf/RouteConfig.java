package uw.mydb.common.conf;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由规则配置实体（proxy 与 center 共享），描述一张分片表如何根据 routeKey 计算目标分片。
 * <p>
 * {@link #routeAlgorithm} 指定算法实现类名（见 {@code uw.mydb.proxy.route.algorithm.*}），
 * {@link #routeParamMap} 提供算法参数（如模数、预设映射表等）。
 * 支持单级父路由继承（{@link #parentId}），用于复用通用路由配置。
 */
public class RouteConfig {

    /**
     * 路由配置 ID（唯一）。
     */
    private long id;
    /**
     * 父路由 ID（继承父路由信息，仅支持一级继承；0 表示无父路由）。
     */
    private long parentId;
    /**
     * 路由名称（展示用）。
     */
    private String routeName;
    /**
     * 路由键名（如 "user_id"、"saas_id"），SQL 中该列的值作为路由输入。
     */
    private String routeKey;
    /**
     * 路由算法实现类全限定名（如 {@code uw.mydb.proxy.route.algorithm.RouteTableByMod}）。
     */
    private String routeAlgorithm;
    /**
     * 路由算法参数（如 modulus、preset 映射等），可能为空。
     */
    private Map<String, String> routeParamMap = new HashMap<>();
    /**
     * 配置最后更新时间戳（毫秒）。
     */
    private long lastUpdate;

    /**
     * 默认构造器（反序列化用）。
     */
    public RouteConfig() {
    }

    /**
     * 全参构造器。
     *
     * @param id             路由 ID
     * @param parentId       父路由 ID
     * @param routeName      路由名
     * @param routeKey       路由键
     * @param routeAlgorithm 路由算法类名
     * @param routeParamMap  路由参数
     */
    public RouteConfig(long id, long parentId, String routeName, String routeKey, String routeAlgorithm, Map<String, String> routeParamMap) {
        this.id = id;
        this.parentId = parentId;
        this.routeName = routeName;
        this.routeKey = routeKey;
        this.routeAlgorithm = routeAlgorithm;
        this.routeParamMap = routeParamMap;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public void setRouteKey(String routeKey) {
        this.routeKey = routeKey;
    }

    public String getRouteAlgorithm() {
        return routeAlgorithm;
    }

    public void setRouteAlgorithm(String routeAlgorithm) {
        this.routeAlgorithm = routeAlgorithm;
    }

    public Map<String, String> getRouteParamMap() {
        return routeParamMap;
    }

    public void setRouteParamMap(Map<String, String> routeParamMap) {
        this.routeParamMap = routeParamMap;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
