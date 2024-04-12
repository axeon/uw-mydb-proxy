package uw.mydb.stats.vo;

/**
 * 错误Sql统计。
 *
 * @author axeon
 */
public class ErrorSql {

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

    /**
     * 错误号。
     */
    private int errorCode;

    /**
     * 错误信息。
     */
    private String errorMsg;

    /**
     * 异常信息。
     */
    private String exception;

    public ErrorSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, int rowNum, long txBytes, long rxBytes, long exeMillis, long runDate, int errorCode, String errorMsg, String exception) {
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
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.exception = exception;
    }

    public ErrorSql() {
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

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }
}
