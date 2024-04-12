package uw.mydb.stats.vo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MyDb proxy 运行报告
 *
 * @author axeon
 */
public class ProxyRunReport {

    /**
     * proxyId
     */
    private long proxyId;

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
     * 连接数量。
     */
    private int connectionNum;

    /**
     * 代理 sql stats统计。
     */
    private SqlStats ProxySqlStats;

    /**
     * 客户端连接信息。
     */
    private Map<String,Long> clientConnMap;

    /**
     * mysql链接信息。
     */
    private List<long[]> mysqlConnList;

    /**
     * 分组统计sqlStats集合。
     */
    private Collection<SchemaSqlStats> schemaSqlStatsList;

    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
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
        this.ProxyName = proxyName;
    }

    public String getProxyVersion() {
        return ProxyVersion;
    }

    public void setProxyVersion(String proxyVersion) {
        this.ProxyVersion = proxyVersion;
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


    public int getConnectionNum() {
        return connectionNum;
    }

    public void setConnectionNum(int connectionNum) {
        this.connectionNum = connectionNum;
    }

    public Map<String, Long> getClientConnMap() {
        return clientConnMap;
    }

    public void setClientConnMap(Map<String, Long> clientConnMap) {
        this.clientConnMap = clientConnMap;
    }

    public List<long[]> getMysqlConnList() {
        return mysqlConnList;
    }

    public void setMysqlConnList(List<long[]> mysqlConnList) {
        this.mysqlConnList = mysqlConnList;
    }

    public SqlStats getProxySqlStats() {
        return ProxySqlStats;
    }

    public void setProxySqlStats(SqlStats proxySqlStats) {
        ProxySqlStats = proxySqlStats;
    }

    public Collection<SchemaSqlStats> getSchemaSqlStatsList() {
        return schemaSqlStatsList;
    }

    public void setSchemaSqlStatsList(Collection<SchemaSqlStats> schemaSqlStatsList) {
        this.schemaSqlStatsList = schemaSqlStatsList;
    }
}
