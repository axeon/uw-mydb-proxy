package uw.mydb.vo;

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
     * 更新时间戳。
     */
    private long lastUpdate;

    public MydbProxyConfig() {
    }

    public MydbProxyConfig(String configKey, String username, String password, long baseCluster) {
        this.configKey = configKey;
        this.username = username;
        this.password = password;
        this.baseCluster = baseCluster;
        this.lastUpdate = System.currentTimeMillis();
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
