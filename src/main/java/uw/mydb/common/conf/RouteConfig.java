package uw.mydb.common.conf;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由规则配置。
 */
public class RouteConfig {

    public RouteConfig(long id, long parentId, String routeName, String routeKey, String routeAlgorithm, Map<String, String> routeParamMap) {
        this.id = id;
        this.parentId = parentId;
        this.routeName = routeName;
        this.routeKey = routeKey;
        this.routeAlgorithm = routeAlgorithm;
        this.routeParamMap = routeParamMap;
    }

    public RouteConfig() {
    }

    /**
     * 路由配置名称。
     */
    private long id;

    /**
     * 上级路由名称，会继承上级路由的信息，只能继承一级。
     */
    private long parentId;

    /**
     * 路由名。
     */
    private String routeName;

    /**
     * 路由键。
     */
    private String routeKey;

    /**
     * 路由算法。
     */
    private String routeAlgorithm;

    /**
     * 路由参数，可能为空。
     */
    private Map<String, String> routeParamMap = new HashMap<>();

    /**
     * 更新时间戳。
     */
    private long lastUpdate;

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
