package uw.mydb.stats.vo;

/**
 * 慢Sql统计。
 *
 * @author axeon
 */
public class SlowSql {

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
    private String database;

    /**
     * 表名。
     */
    private String table;

    /**
     * sql
     */
    private String sql;

    /**
     * sql类型。
     */
    private String sqlType;

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

    public SlowSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, String sqlType, int rowNum, long txBytes, long rxBytes, long exeMillis, long runDate) {
        this.clientIp = clientIp;
        this.clusterId = clusterId;
        this.serverId = serverId;
        this.database = database;
        this.table = table;
        this.sql = sql;
        this.sqlType = sqlType;
        this.rowNum = rowNum;
        this.txBytes = txBytes;
        this.rxBytes = rxBytes;
        this.exeMillis = exeMillis;
        this.runDate = runDate;
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

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getSqlType() {
        return sqlType;
    }

    public void setSqlType(String sqlType) {
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
