package uw.mydb.common.conf;

/**
 * mydb proxy配置类。
 *
 * @author axeon
 */
public class MydbProxyConfig {

    /**
     * 配置Key
     */
    private String configKey;

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
     * 基础cluster。
     */
    private long baseCluster;

    /**
     * 集群ID序列
     */
    private String clusterIds;

    /**
     * 每节点saas数
     */
    private int saasPerNode;

    /**
     * 更新时间戳。
     */
    private long lastUpdate;

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
