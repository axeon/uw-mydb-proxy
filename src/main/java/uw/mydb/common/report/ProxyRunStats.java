package uw.mydb.common.report;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final long reportVersion = System.currentTimeMillis();
    /**
     * proxyId
     */
    private long proxyId;
    /**
     * 报告次数。
     */
    private AtomicLong reportCount = new AtomicLong();

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
    private Map<String, Long> clientConnMap;
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
     * schema统计数量。
     */
    private int schemaStatsNum;

    /**
     * insert计数。
     */
    private AtomicInteger insertNum = new AtomicInteger();
    /**
     * update计数。
     */
    private AtomicInteger updateNum = new AtomicInteger();
    /**
     * delete计数。
     */
    private AtomicInteger deleteNum = new AtomicInteger();
    /**
     * select计数。
     */
    private AtomicInteger selectNum = new AtomicInteger();
    /**
     * other计数。
     */
    private AtomicInteger otherNum = new AtomicInteger();
    /**
     * insert错误计数。
     */
    private AtomicInteger insertErrorNum = new AtomicInteger();
    /**
     * update错误计数。
     */
    private AtomicInteger updateErrorNum = new AtomicInteger();
    /**
     * delete错误计数。
     */
    private AtomicInteger deleteErrorNum = new AtomicInteger();
    /**
     * select错误计数。
     */
    private AtomicInteger selectErrorNum = new AtomicInteger();
    /**
     * other错误计数。
     */
    private AtomicInteger otherErrorNum = new AtomicInteger();
    /**
     * insert执行耗时毫秒数。
     */
    private AtomicLong insertExeMillis = new AtomicLong();
    /**
     * update执行耗时毫秒数。
     */
    private AtomicLong updateExeMillis = new AtomicLong();
    /**
     * delete执行耗时毫秒数。
     */
    private AtomicLong deleteExeMillis = new AtomicLong();
    /**
     * select执行耗时毫秒数。
     */
    private AtomicLong selectExeMillis = new AtomicLong();
    /**
     * other执行耗时毫秒数。
     */
    private AtomicLong otherExeMillis = new AtomicLong();
    /**
     * insert影响行数。
     */
    private AtomicLong insertRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private AtomicLong updateRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private AtomicLong deleteRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private AtomicLong selectRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private AtomicLong otherRowNum = new AtomicLong();
    /**
     * insert发送字节数。
     */
    private AtomicLong insertTxBytes = new AtomicLong();
    /**
     * insert接收字节数。
     */
    private AtomicLong insertRxBytes = new AtomicLong();
    /**
     * update发送字节数。
     */
    private AtomicLong updateTxBytes = new AtomicLong();
    /**
     * update接收字节数。
     */
    private AtomicLong updateRxBytes = new AtomicLong();
    /**
     * delete发送字节数。
     */
    private AtomicLong deleteTxBytes = new AtomicLong();
    /**
     * delete接收字节数。
     */
    private AtomicLong deleteRxBytes = new AtomicLong();
    /**
     * select发送字节数。
     */
    private AtomicLong selectTxBytes = new AtomicLong();
    /**
     * select接收字节数。
     */
    private AtomicLong selectRxBytes = new AtomicLong();
    /**
     * other发送字节数。
     */
    private AtomicLong otherTxBytes = new AtomicLong();
    /**
     * other接收字节数。
     */
    private AtomicLong otherRxBytes = new AtomicLong();


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

    public int getSchemaStatsNum() {
        return schemaStatsNum;
    }

    public void setSchemaStatsNum(int schemaStatsNum) {
        this.schemaStatsNum = schemaStatsNum;
    }


    public int getInsertNum() {
        return insertNum.get();
    }

    public void addInsertNum(int insertNum) {
        this.insertNum.addAndGet( insertNum );
    }

    public int getUpdateNum() {
        return updateNum.get();
    }

    public void addUpdateNum(int updateNum) {
        this.updateNum.addAndGet( updateNum );
    }

    public int getDeleteNum() {
        return deleteNum.get();
    }

    public void addDeleteNum(int deleteNum) {
        this.deleteNum.addAndGet( deleteNum );
    }

    public int getSelectNum() {
        return selectNum.get();
    }

    public void addSelectNum(int selectNum) {
        this.selectNum.addAndGet( selectNum );
    }

    public int getOtherNum() {
        return otherNum.get();
    }

    public void addOtherNum(int otherNum) {
        this.otherNum.addAndGet( otherNum );
    }

    public int getInsertErrorNum() {
        return insertErrorNum.get();
    }

    public void addInsertErrorNum(int insertErrorNum) {
        this.insertErrorNum.addAndGet( insertErrorNum );
    }

    public int getUpdateErrorNum() {
        return updateErrorNum.get();
    }

    public void addUpdateErrorNum(int updateErrorNum) {
        this.updateErrorNum.addAndGet( updateErrorNum );
    }

    public int getDeleteErrorNum() {
        return deleteErrorNum.get();
    }

    public void addDeleteErrorNum(int deleteErrorNum) {
        this.deleteErrorNum.addAndGet( deleteErrorNum );
    }

    public int getSelectErrorNum() {
        return selectErrorNum.get();
    }

    public void addSelectErrorNum(int selectErrorNum) {
        this.selectErrorNum.addAndGet( selectErrorNum );
    }

    public int getOtherErrorNum() {
        return otherErrorNum.get();
    }

    public void addOtherErrorNum(int otherErrorNum) {
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
