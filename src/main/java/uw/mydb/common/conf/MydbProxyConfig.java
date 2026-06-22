package uw.mydb.common.conf;

/**
 * mydb proxy 顶层配置实体（proxy 与 center 共享），由 center 下发给 proxy。
 * <p>
 * 一个 configKey 对应一套 proxy 集群配置，包含对外认证用户名/密码、基础集群（未命中路由时的兜底集群）、
 * 可用集群 ID 列表、每节点 SaaS 容量等。
 *
 * @author axeon
 */
public class MydbProxyConfig {

    /**
     * 配置 Key，唯一标识一套 proxy 配置（多租户/多环境隔离）。
     */
    private String configKey;

    /**
     * 配置显示名（管理后台展示用）。
     */
    private String configName;

    /**
     * proxy 对外认证用户名（MySQL 客户端连接时使用）。
     */
    private String username;

    /**
     * proxy 对外认证密码（明文存储，由 center 安全下发）。
     */
    private String password;

    /**
     * 基础集群 ID。未配置路由的表 / 未命中路由的 SQL 都落到该集群执行。
     */
    private long baseCluster;

    /**
     * 该 proxy 可用的 MySQL 集群 ID 列表（逗号分隔），用于校验与容量规划。
     */
    private String clusterIds;

    /**
     * 每个数据节点承载的 SaaS 数量上限（用于 SaaS 分库路由算法）。
     */
    private int saasPerNode;

    /**
     * 配置最后更新时间戳（毫秒），用于缓存失效与版本判断。
     */
    private long lastUpdate;

    /**
     * 默认构造器（反序列化用）。
     */
    public MydbProxyConfig() {
    }

    /**
     * 全参构造器。
     *
     * @param configKey    配置 Key
     * @param configName   配置名
     * @param username     认证用户名
     * @param password     认证密码
     * @param baseCluster  基础集群 ID
     * @param clusterIds   可用集群 ID 列表
     * @param saasPerNode  每节点 SaaS 数
     * @param lastUpdate   更新时间戳
     */
    public MydbProxyConfig(String configKey, String configName, String username, String password, long baseCluster, String clusterIds, int saasPerNode, long lastUpdate) {
        this.configKey = configKey;
        this.configName = configName;
        this.username = username;
        this.password = password;
        this.baseCluster = baseCluster;
        this.clusterIds = clusterIds;
        this.saasPerNode = saasPerNode;
        this.lastUpdate = lastUpdate;
    }

    public String getClusterIds() {
        return clusterIds;
    }

    public void setClusterIds(String clusterIds) {
        this.clusterIds = clusterIds;
    }

    public int getSaasPerNode() {
        return saasPerNode;
    }

    public void setSaasPerNode(int saasPerNode) {
        this.saasPerNode = saasPerNode;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
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

    public long getBaseCluster() {
        return baseCluster;
    }

    public void setBaseCluster(long baseCluster) {
        this.baseCluster = baseCluster;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

}
