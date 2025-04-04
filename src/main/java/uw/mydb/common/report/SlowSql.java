package uw.mydb.common.report;

/**
 * 慢Sql统计。
 *
 * @author axeon
 */
public class SlowSql {

    private long proxyId;
    /**
     * 发起客户端。
     */
    private String clientIp;

    /**
     * 集群ID。
     */
    private long clusterId;

    /**
     * 服务器ID。
     */
    private long serverId;

    /**
     * 数据库名。
     */
    private String dbName;

    /**
     * 表名。
     */
    private String tableName;

    /**
     * sql
     */
    private String sqlInfo;

    /**
     * sql类型。
     */
    private int sqlType;

    /**
     * 数据行计数。
     */
    private int rowNum;

    /**
     * 发送字节数。
     */
    private long txBytes;

    /**
     * 接收字节数。
     */
    private long rxBytes;

    /**
     * 执行毫秒数。
     */
    private long exeMillis;

    /**
     * 运行时间。
     */
    private long runDate;

    public SlowSql() {
    }

    public SlowSql(String clientIp, long clusterId, long serverId, String dbName, String tableName, String sqlInfo, int sqlType, int rowNum, long txBytes,
                   long rxBytes, long exeMillis, long runDate) {
        this.clientIp = clientIp;
        this.clusterId = clusterId;
        this.serverId = serverId;
        this.dbName = dbName;
        this.tableName = tableName;
        this.sqlInfo = sqlInfo;
        this.sqlType = sqlType;
        this.rowNum = rowNum;
        this.txBytes = txBytes;
        this.rxBytes = rxBytes;
        this.exeMillis = exeMillis;
        this.runDate = runDate;
    }

    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public long getClusterId() {
        return clusterId;
    }

    public void setClusterId(long clusterId) {
        this.clusterId = clusterId;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSqlInfo() {
        return sqlInfo;
    }

    public void setSqlInfo(String sqlInfo) {
        this.sqlInfo = sqlInfo;
    }

    public int getSqlType() {
        return sqlType;
    }

    public void setSqlType(int sqlType) {
        this.sqlType = sqlType;
    }

    public int getRowNum() {
        return rowNum;
    }

    public void setRowNum(int rowNum) {
        this.rowNum = rowNum;
    }

    public long getTxBytes() {
        return txBytes;
    }

    public void setTxBytes(long txBytes) {
        this.txBytes = txBytes;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public long getExeMillis() {
        return exeMillis;
    }

    public void setExeMillis(long exeMillis) {
        this.exeMillis = exeMillis;
    }

    public long getRunDate() {
        return runDate;
    }

    public void setRunDate(long runDate) {
        this.runDate = runDate;
    }
}
