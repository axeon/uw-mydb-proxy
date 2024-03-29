package uw.mydb.vo;

/**
 * mysql服务器配置
 */
public class MysqlServerConfig {
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
    private String user;
    /**
     * 密码
     */
    private String pass;
    /**
     * 线程数。
     */
    private int threadNum = 0;
    /**
     * 最大连接数
     */
    private int connMax = 1000;
    /**
     * 最小连接数
     */
    private int connMin = 1;
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

    public MysqlServerConfig(int weight, String host, int port, String user, String pass, int threadNum, int connMin, int connMax, int connIdleTimeout, int connBusyTimeout,
                             int connMaxAge) {
        this.weight = weight;
        this.host = host;
        this.port = port;
        this.user = user;
        this.pass = pass;
        this.threadNum = threadNum;
        this.connMax = connMax;
        this.connMin = connMin;
        this.connIdleTimeout = connIdleTimeout;
        this.connBusyTimeout = connBusyTimeout;
        this.connMaxAge = connMaxAge;
    }

    @Override
    public String toString() {
        return user + "@" + host + ":" + port;
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

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public int getConnMax() {
        return connMax;
    }

    public void setConnMax(int connMax) {
        this.connMax = connMax;
    }

    public int getConnMin() {
        return connMin;
    }

    public void setConnMin(int connMin) {
        this.connMin = connMin;
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
