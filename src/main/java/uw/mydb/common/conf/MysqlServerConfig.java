package uw.mydb.common.conf;

import uw.mydb.proxy.constant.MysqlServerType;

/**
 * MySQL 单台服务器配置实体（proxy 与 center 共享），描述一台 MySQL 实例的连接信息与连接池参数。
 * <p>
 * 一个 {@link MysqlServerConfig} 隶属于一个 {@link MysqlClusterConfig}，按 serverType（master/slave/read-only）
 * 与 weight 参与集群的读写负载均衡。
 */
public class MysqlServerConfig {

    /**
     * 服务器配置 ID（唯一）。
     */
    private long id;

    /**
     * 所在集群 ID。
     */
    private long clusterId;

    /**
     * 服务器类型（master/slave/read-only 等，见 {@link MysqlServerType}），默认 master。
     */
    private int serverType = MysqlServerType.MASTER.getValue();

    /**
     * 读权重（&gt;=1），用于轮询列表展开，默认 1。
     */
    private int weight = 1;

    /**
     * MySQL 主机 IP/域名。
     */
    private String host;

    /**
     * MySQL 监听端口（通常 3306）。
     */
    private int port;

    /**
     * 连接 MySQL 使用的用户名。
     */
    private String username;

    /**
     * 连接 MySQL 使用的密码。
     */
    private String password;

    /**
     * 连接池最小空闲连接数，默认 1。
     */
    private int connMin = 1;

    /**
     * 连接池最大连接数上限，默认 1000。
     */
    private int connMax = 1000;

    /**
     * 连接空闲超时（秒），超过则回收，默认 180。
     */
    private int connIdleTimeout = 180;

    /**
     * 连接繁忙超时（秒）：连接被借出后超过该时间未归还则视为异常，默认 180。
     */
    private int connBusyTimeout = 180;

    /**
     * 连接最大存活寿命（秒），超过则强制回收重建，避免长连接老化，默认 1800。
     */
    private int connMaxAge = 1800;

    /**
     * 默认构造器（反序列化用）。
     */
    public MysqlServerConfig() {
    }

    /**
     * 全参构造器。
     *
     * @param id              服务器 ID
     * @param clusterId       集群 ID
     * @param serverType      服务器类型
     * @param weight          读权重
     * @param host            主机
     * @param port            端口
     * @param username        用户名
     * @param password        密码
     * @param connMin         最小连接数
     * @param connMax         最大连接数
     * @param connIdleTimeout 空闲超时秒数
     * @param connBusyTimeout 繁忙超时秒数
     * @param connMaxAge      最大寿命秒数
     */
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
