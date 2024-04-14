package uw.mydb.stats.vo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MyDb proxy 运行报告
 *
 * @author axeon
 */
public class ProxyRunStats extends SqlStats{

    /**
     * proxyId
     */
    private long proxyId;

    /**
     * 配置key。
     */
    private String configKey;

    /**
     * proxyHost。
     */
    private String proxyHost;

    /**
     * proxyPort。
     */
    private int proxyPort;

    /**
     * 应用名称
     */
    private String ProxyName;

    /**
     * 应用版本
     */
    private String ProxyVersion;

    /**
     * CPU负载。
     */
    private double cpuLoad;

    /**
     * jvm内存总数
     */
    private long jvmMemMax;

    /**
     * jvm内存总数
     */
    private long jvmMemTotal;

    /**
     * jvm空闲内存
     */
    private long jvmMemFree;

    /**
     * 活跃线程
     */
    private int threadActive;

    /**
     * 峰值线程
     */
    private int threadPeak;

    /**
     * 守护线程
     */
    private int threadDaemon;

    /**
     * 累计启动线程
     */
    private long threadStarted;

    /**
     * 客户端数。
     */
    private int clientNum;

    /**
     * 客户端连接数量。
     */
    private int clientConnNum;

    /**
     * 客户端连接信息。
     */
    private Map<String,Long> clientConnMap;

    /**
     * mysql数量。
     */
    private int mysqlNum;

    /**
     * mysql忙连接数量。
     */
    private int mysqlBusyConnNum;

    /**
     * mysql空闲链接数量。
     */
    private int mysqlIdleConnNum;

    /**
     * mysql链接信息。
     */
    private List<long[]> mysqlConnList;

    /**
     * 分组统计sqlStats集合。
     */
    private Collection<SchemaRunStats> schemaRunStatsList;

    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyName() {
        return ProxyName;
    }

    public void setProxyName(String proxyName) {
        ProxyName = proxyName;
    }

    public String getProxyVersion() {
        return ProxyVersion;
    }

    public void setProxyVersion(String proxyVersion) {
        ProxyVersion = proxyVersion;
    }

    public double getCpuLoad() {
        return cpuLoad;
    }

    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public long getJvmMemMax() {
        return jvmMemMax;
    }

    public void setJvmMemMax(long jvmMemMax) {
        this.jvmMemMax = jvmMemMax;
    }

    public long getJvmMemTotal() {
        return jvmMemTotal;
    }

    public void setJvmMemTotal(long jvmMemTotal) {
        this.jvmMemTotal = jvmMemTotal;
    }

    public long getJvmMemFree() {
        return jvmMemFree;
    }

    public void setJvmMemFree(long jvmMemFree) {
        this.jvmMemFree = jvmMemFree;
    }

    public int getThreadActive() {
        return threadActive;
    }

    public void setThreadActive(int threadActive) {
        this.threadActive = threadActive;
    }

    public int getThreadPeak() {
        return threadPeak;
    }

    public void setThreadPeak(int threadPeak) {
        this.threadPeak = threadPeak;
    }

    public int getThreadDaemon() {
        return threadDaemon;
    }

    public void setThreadDaemon(int threadDaemon) {
        this.threadDaemon = threadDaemon;
    }

    public long getThreadStarted() {
        return threadStarted;
    }

    public void setThreadStarted(long threadStarted) {
        this.threadStarted = threadStarted;
    }

    public int getClientNum() {
        return clientNum;
    }

    public void setClientNum(int clientNum) {
        this.clientNum = clientNum;
    }

    public int getClientConnNum() {
        return clientConnNum;
    }

    public void setClientConnNum(int clientConnNum) {
        this.clientConnNum = clientConnNum;
    }

    public Map<String, Long> getClientConnMap() {
        return clientConnMap;
    }

    public void setClientConnMap(Map<String, Long> clientConnMap) {
        this.clientConnMap = clientConnMap;
    }

    public int getMysqlNum() {
        return mysqlNum;
    }

    public void setMysqlNum(int mysqlNum) {
        this.mysqlNum = mysqlNum;
    }

    public int getMysqlBusyConnNum() {
        return mysqlBusyConnNum;
    }

    public void setMysqlBusyConnNum(int mysqlBusyConnNum) {
        this.mysqlBusyConnNum = mysqlBusyConnNum;
    }

    public int getMysqlIdleConnNum() {
        return mysqlIdleConnNum;
    }

    public void setMysqlIdleConnNum(int mysqlIdleConnNum) {
        this.mysqlIdleConnNum = mysqlIdleConnNum;
    }

    public List<long[]> getMysqlConnList() {
        return mysqlConnList;
    }

    public void setMysqlConnList(List<long[]> mysqlConnList) {
        this.mysqlConnList = mysqlConnList;
    }

    public Collection<SchemaRunStats> getSchemaRunStatsList() {
        return schemaRunStatsList;
    }

    public void setSchemaRunStatsList(Collection<SchemaRunStats> schemaRunStatsList) {
        this.schemaRunStatsList = schemaRunStatsList;
    }
}
