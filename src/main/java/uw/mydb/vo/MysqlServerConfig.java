package uw.mydb.vo;

import uw.mydb.constant.MysqlServerType;

/**
 * mysql服务器配置
 */
public class MysqlServerConfig {

    /**
     * 服务器配置ID
     */
    private long id;

    /**
     * 所在集群ID
     */
    private long clusterId;

    /**
     * 服务器类型。
     */
    private int serverType = MysqlServerType.MASTER.getValue();

    /**
     * 读取权重
     */
    private int weight = 1;

    /**
     * 主机
     */
    private String host;

    /**
     * 端口号
     */
    private int port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 最小连接数
     */
    private int connMin = 1;

    /**
     * 最大连接数
     */
    private int connMax = 1000;

    /**
     * 连接闲时超时秒数.
     */
    private int connIdleTimeout = 180;

    /**
     * 连接忙时超时秒数.
     */
    private int connBusyTimeout = 180;

    /**
     * 连接最大寿命秒数.
     */
    private int connMaxAge = 1800;

    public MysqlServerConfig() {
    }

    public MysqlServerConfig(long id, long clusterId, int serverType, int weight, String host, int port, String username, String password, int connMin, int connMax, int connIdleTimeout, int connBusyTimeout, int connMaxAge) {
        this.id = id;
        this.clusterId = clusterId;
        this.serverType = serverType;
        this.weight = weight;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.connMin = connMin;
        this.connMax = connMax;
        this.connIdleTimeout = connIdleTimeout;
        this.connBusyTimeout = connBusyTimeout;
        this.connMaxAge = connMaxAge;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MysqlServerConfig that = (MysqlServerConfig) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        return username + "@" + host + ":" + port;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getClusterId() {
        return clusterId;
    }

    public void setClusterId(long clusterId) {
        this.clusterId = clusterId;
    }

    public int getServerType() {
        return serverType;
    }

    public void setServerType(int serverType) {
        this.serverType = serverType;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public int getConnMin() {
        return connMin;
    }

    public void setConnMin(int connMin) {
        this.connMin = connMin;
    }

    public int getConnMax() {
        return connMax;
    }

    public void setConnMax(int connMax) {
        this.connMax = connMax;
    }

    public int getConnIdleTimeout() {
        return connIdleTimeout;
    }

    public void setConnIdleTimeout(int connIdleTimeout) {
        this.connIdleTimeout = connIdleTimeout;
    }

    public int getConnBusyTimeout() {
        return connBusyTimeout;
    }

    public void setConnBusyTimeout(int connBusyTimeout) {
        this.connBusyTimeout = connBusyTimeout;
    }

    public int getConnMaxAge() {
        return connMaxAge;
    }

    public void setConnMaxAge(int connMaxAge) {
        this.connMaxAge = connMaxAge;
    }
}
