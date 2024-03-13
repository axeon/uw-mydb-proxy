package uw.mydb.vo;

import java.util.Map;
import java.util.Set;

/**
 * mydb完整配置，仅用于初次传输数据。
 *
 * @author axeon
 */
public class MydbFullConfig {

    /**
     * 配置ID
     */
    private long configId;

    /**
     * 配置名。
     */
    private String configName;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * mysql cluster ids。
     */
    private String mysqlClusters;

    /**
     * 基础cluster。
     */
    private long baseCluster;

    /**
     * 更新时间戳。
     */
    private long lastUpdate;

    /**
     * 表配置Map。
     */
    private Map<String, TableConfig> tableMap;

    /**
     * 路由配置Map。
     */
    private Map<Long, RouteConfig> routeMap;

    /**
     * saas节点Map。
     */
    private Map<String, DataNode> saasNodeMap;

    /**
     * mysql集群map。
     */
    private Map<Long, MysqlClusterConfig> mysqlClusterMap;

    /**
     * schemaMap。
     */
    private Map<String, Set<String>> schemaMetaMap;

    public long getConfigId() {
        return configId;
    }

    public void setConfigId(long configId) {
        this.configId = configId;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMysqlClusters() {
        return mysqlClusters;
    }

    public void setMysqlClusters(String mysqlClusters) {
        this.mysqlClusters = mysqlClusters;
    }

    public long getBaseCluster() {
        return baseCluster;
    }

    public void setBaseCluster(long baseCluster) {
        this.baseCluster = baseCluster;
    }

    public Map<String, TableConfig> getTableMap() {
        return tableMap;
    }

    public void setTableMap(Map<String, TableConfig> tableMap) {
        this.tableMap = tableMap;
    }

    public Map<Long, RouteConfig> getRouteMap() {
        return routeMap;
    }

    public void setRouteMap(Map<Long, RouteConfig> routeMap) {
        this.routeMap = routeMap;
    }

    public Map<String, DataNode> getSaasNodeMap() {
        return saasNodeMap;
    }

    public void setSaasNodeMap(Map<String, DataNode> saasNodeMap) {
        this.saasNodeMap = saasNodeMap;
    }

    public Map<Long, MysqlClusterConfig> getMysqlClusterMap() {
        return mysqlClusterMap;
    }

    public void setMysqlClusterMap(Map<Long, MysqlClusterConfig> mysqlClusterMap) {
        this.mysqlClusterMap = mysqlClusterMap;
    }

    public Map<String, Set<String>> getSchemaMetaMap() {
        return schemaMetaMap;
    }

    public void setSchemaMetaMap(Map<String, Set<String>> schemaMetaMap) {
        this.schemaMetaMap = schemaMetaMap;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

}
