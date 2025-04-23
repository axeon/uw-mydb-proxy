package uw.mydb.common.report;

import uw.common.util.SystemClock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MyDb proxy 运行报告
 *
 * @author axeon
 */
public class ProxyRunStats {

    /**
     * 报告版本。
     */
    private final long reportVersion = SystemClock.now();
    /**
     * proxyId
     */
    private long proxyId;
    /**
     * 报告次数。
     */
    private final AtomicLong reportCount = new AtomicLong();

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
    private long clientNum;
    /**
     * 客户端连接数量。
     */
    private long clientConnNum;
    /**
     * 客户端连接信息。
     */
    private Map<String, Long> clientConnMap;
    /**
     * mysql数量。
     */
    private int mysqlNum;
    /**
     * mysql忙连接数量。
     */
    private long mysqlBusyConnNum;
    /**
     * mysql空闲链接数量。
     */
    private long mysqlIdleConnNum;
    /**
     * mysql链接信息。
     */
    private List<long[]> mysqlConnList;
    /**
     * schema统计数量。
     */
    private long schemaStatsNum;

    /**
     * insert计数。
     */
    private final AtomicLong insertNum = new AtomicLong();
    /**
     * update计数。
     */
    private final AtomicLong updateNum = new AtomicLong();
    /**
     * delete计数。
     */
    private final AtomicLong deleteNum = new AtomicLong();
    /**
     * select计数。
     */
    private final AtomicLong selectNum = new AtomicLong();
    /**
     * other计数。
     */
    private final AtomicLong otherNum = new AtomicLong();
    /**
     * insert错误计数。
     */
    private final AtomicLong insertErrorNum = new AtomicLong();
    /**
     * update错误计数。
     */
    private final AtomicLong updateErrorNum = new AtomicLong();
    /**
     * delete错误计数。
     */
    private final AtomicLong deleteErrorNum = new AtomicLong();
    /**
     * select错误计数。
     */
    private final AtomicLong selectErrorNum = new AtomicLong();
    /**
     * other错误计数。
     */
    private final AtomicLong otherErrorNum = new AtomicLong();
    /**
     * insert执行耗时毫秒数。
     */
    private final AtomicLong insertExeMillis = new AtomicLong();
    /**
     * update执行耗时毫秒数。
     */
    private final AtomicLong updateExeMillis = new AtomicLong();
    /**
     * delete执行耗时毫秒数。
     */
    private final AtomicLong deleteExeMillis = new AtomicLong();
    /**
     * select执行耗时毫秒数。
     */
    private final AtomicLong selectExeMillis = new AtomicLong();
    /**
     * other执行耗时毫秒数。
     */
    private final AtomicLong otherExeMillis = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong insertRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong updateRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong deleteRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong selectRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong otherRowNum = new AtomicLong();
    /**
     * insert发送字节数。
     */
    private final AtomicLong insertTxBytes = new AtomicLong();
    /**
     * insert接收字节数。
     */
    private final AtomicLong insertRxBytes = new AtomicLong();
    /**
     * update发送字节数。
     */
    private final AtomicLong updateTxBytes = new AtomicLong();
    /**
     * update接收字节数。
     */
    private final AtomicLong updateRxBytes = new AtomicLong();
    /**
     * delete发送字节数。
     */
    private final AtomicLong deleteTxBytes = new AtomicLong();
    /**
     * delete接收字节数。
     */
    private final AtomicLong deleteRxBytes = new AtomicLong();
    /**
     * select发送字节数。
     */
    private final AtomicLong selectTxBytes = new AtomicLong();
    /**
     * select接收字节数。
     */
    private final AtomicLong selectRxBytes = new AtomicLong();
    /**
     * other发送字节数。
     */
    private final AtomicLong otherTxBytes = new AtomicLong();
    /**
     * other接收字节数。
     */
    private final AtomicLong otherRxBytes = new AtomicLong();


    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
    }

    public long getReportVersion() {
        return reportVersion;
    }

    public long getReportCount() {
        return reportCount.incrementAndGet();
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

    public long getClientNum() {
        return clientNum;
    }

    public void setClientNum(long clientNum) {
        this.clientNum = clientNum;
    }

    public long getClientConnNum() {
        return clientConnNum;
    }

    public void setClientConnNum(long clientConnNum) {
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

    public long getMysqlBusyConnNum() {
        return mysqlBusyConnNum;
    }

    public void setMysqlBusyConnNum(long mysqlBusyConnNum) {
        this.mysqlBusyConnNum = mysqlBusyConnNum;
    }

    public long getMysqlIdleConnNum() {
        return mysqlIdleConnNum;
    }

    public void setMysqlIdleConnNum(long mysqlIdleConnNum) {
        this.mysqlIdleConnNum = mysqlIdleConnNum;
    }

    public List<long[]> getMysqlConnList() {
        return mysqlConnList;
    }

    public void setMysqlConnList(List<long[]> mysqlConnList) {
        this.mysqlConnList = mysqlConnList;
    }

    public long getSchemaStatsNum() {
        return schemaStatsNum;
    }

    public void setSchemaStatsNum(int schemaStatsNum) {
        this.schemaStatsNum = schemaStatsNum;
    }


    public long getInsertNum() {
        return insertNum.get();
    }

    public void addInsertNum(long insertNum) {
        this.insertNum.addAndGet( insertNum );
    }

    public long getUpdateNum() {
        return updateNum.get();
    }

    public void addUpdateNum(long updateNum) {
        this.updateNum.addAndGet( updateNum );
    }

    public long getDeleteNum() {
        return deleteNum.get();
    }

    public void addDeleteNum(long deleteNum) {
        this.deleteNum.addAndGet( deleteNum );
    }

    public long getSelectNum() {
        return selectNum.get();
    }

    public void addSelectNum(long selectNum) {
        this.selectNum.addAndGet( selectNum );
    }

    public long getOtherNum() {
        return otherNum.get();
    }

    public void addOtherNum(long otherNum) {
        this.otherNum.addAndGet( otherNum );
    }

    public long getInsertErrorNum() {
        return insertErrorNum.get();
    }

    public void addInsertErrorNum(long insertErrorNum) {
        this.insertErrorNum.addAndGet( insertErrorNum );
    }

    public long getUpdateErrorNum() {
        return updateErrorNum.get();
    }

    public void addUpdateErrorNum(long updateErrorNum) {
        this.updateErrorNum.addAndGet( updateErrorNum );
    }

    public long getDeleteErrorNum() {
        return deleteErrorNum.get();
    }

    public void addDeleteErrorNum(long deleteErrorNum) {
        this.deleteErrorNum.addAndGet( deleteErrorNum );
    }

    public long getSelectErrorNum() {
        return selectErrorNum.get();
    }

    public void addSelectErrorNum(long selectErrorNum) {
        this.selectErrorNum.addAndGet( selectErrorNum );
    }

    public long getOtherErrorNum() {
        return otherErrorNum.get();
    }

    public void addOtherErrorNum(long otherErrorNum) {
        this.otherErrorNum.addAndGet( otherErrorNum );
    }

    public long getInsertExeMillis() {
        return insertExeMillis.get();
    }

    public void addInsertExeMillis(long insertExeMillis) {
        this.insertExeMillis.addAndGet( insertExeMillis );
    }

    public long getUpdateExeMillis() {
        return updateExeMillis.get();
    }

    public void addUpdateExeMillis(long updateExeMillis) {
        this.updateExeMillis.addAndGet( updateExeMillis );
    }

    public long getDeleteExeMillis() {
        return deleteExeMillis.get();
    }

    public void addDeleteExeMillis(long deleteExeMillis) {
        this.deleteExeMillis.addAndGet( deleteExeMillis );
    }

    public long getSelectExeMillis() {
        return selectExeMillis.get();
    }

    public void addSelectExeMillis(long selectExeMillis) {
        this.selectExeMillis.addAndGet( selectExeMillis );
    }

    public long getOtherExeMillis() {
        return otherExeMillis.get();
    }

    public void addOtherExeMillis(long otherExeMillis) {
        this.otherExeMillis.addAndGet( otherExeMillis );
    }

    public long getInsertRowNum() {
        return insertRowNum.get();
    }

    public void addInsertRowNum(long insertRowNum) {
        this.insertRowNum.addAndGet( insertRowNum );
    }

    public long getUpdateRowNum() {
        return updateRowNum.get();
    }

    public void addUpdateRowNum(long updateRowNum) {
        this.updateRowNum.addAndGet( updateRowNum );
    }

    public long getDeleteRowNum() {
        return deleteRowNum.get();
    }

    public void addDeleteRowNum(long deleteRowNum) {
        this.deleteRowNum.addAndGet( deleteRowNum );
    }

    public long getSelectRowNum() {
        return selectRowNum.get();
    }

    public void addSelectRowNum(long selectRowNum) {
        this.selectRowNum.addAndGet( selectRowNum );
    }

    public long getOtherRowNum() {
        return otherRowNum.get();
    }

    public void addOtherRowNum(long otherRowNum) {
        this.otherRowNum.addAndGet( otherRowNum );
    }

    public long getInsertTxBytes() {
        return insertTxBytes.get();
    }

    public void addInsertTxBytes(long insertTxBytes) {
        this.insertTxBytes.addAndGet( insertTxBytes );
    }

    public long getInsertRxBytes() {
        return insertRxBytes.get();
    }

    public void addInsertRxBytes(long insertRxBytes) {
        this.insertRxBytes.addAndGet( insertRxBytes );
    }

    public long getUpdateTxBytes() {
        return updateTxBytes.get();
    }

    public void addUpdateTxBytes(long updateTxBytes) {
        this.updateTxBytes.addAndGet( updateTxBytes );
    }

    public long getUpdateRxBytes() {
        return updateRxBytes.get();
    }

    public void addUpdateRxBytes(long updateRxBytes) {
        this.updateRxBytes.addAndGet( updateRxBytes );
    }

    public long getDeleteTxBytes() {
        return deleteTxBytes.get();
    }

    public void addDeleteTxBytes(long deleteTxBytes) {
        this.deleteTxBytes.addAndGet( deleteTxBytes );
    }

    public long getDeleteRxBytes() {
        return deleteRxBytes.get();
    }

    public void addDeleteRxBytes(long deleteRxBytes) {
        this.deleteRxBytes.addAndGet( deleteRxBytes );
    }

    public long getSelectTxBytes() {
        return selectTxBytes.get();
    }

    public void addSelectTxBytes(long selectTxBytes) {
        this.selectTxBytes.addAndGet( selectTxBytes );
    }

    public long getSelectRxBytes() {
        return selectRxBytes.get();
    }

    public void addSelectRxBytes(long selectRxBytes) {
        this.selectRxBytes.addAndGet( selectRxBytes );
    }

    public long getOtherTxBytes() {
        return otherTxBytes.get();
    }

    public void addOtherTxBytes(long otherTxBytes) {
        this.otherTxBytes.addAndGet( otherTxBytes );
    }

    public long getOtherRxBytes() {
        return otherRxBytes.get();
    }

    public void addOtherRxBytes(long otherRxBytes) {
        this.otherRxBytes.addAndGet( otherRxBytes );
    }
}
