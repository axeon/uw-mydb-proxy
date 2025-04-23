package uw.mydb.common.report;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于schema的sql统计信息。
 * schema统计数据考虑到服务器端合并的因素，所以每次序列化之后清零。
 *
 * @author axeon
 */
public class SchemaRunStats {
    /**
     * proxyId
     */
    private long proxyId;
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
     * 是否报告给proxy。
     */
    private transient boolean reportProxy;

    /**
     * 是否报告给Schema。
     */
    private transient boolean reportSchema;

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
    private final AtomicLong  updateExeMillis = new AtomicLong();
    /**
     * delete执行耗时毫秒数。
     */
    private final AtomicLong  deleteExeMillis = new AtomicLong();
    /**
     * select执行耗时毫秒数。
     */
    private final AtomicLong  selectExeMillis = new AtomicLong();
    /**
     * other执行耗时毫秒数。
     */
    private final AtomicLong  otherExeMillis = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong  insertRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong  updateRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong  deleteRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong  selectRowNum = new AtomicLong();
    /**
     * insert影响行数。
     */
    private final AtomicLong  otherRowNum = new AtomicLong();
    /**
     * insert发送字节数。
     */
    private final AtomicLong  insertTxBytes = new AtomicLong();
    /**
     * insert接收字节数。
     */
    private final AtomicLong  insertRxBytes = new AtomicLong();
    /**
     * update发送字节数。
     */
    private final AtomicLong  updateTxBytes = new AtomicLong();
    /**
     * update接收字节数。
     */
    private final AtomicLong  updateRxBytes = new AtomicLong();
    /**
     * delete发送字节数。
     */
    private final AtomicLong  deleteTxBytes = new AtomicLong();
    /**
     * delete接收字节数。
     */
    private final AtomicLong  deleteRxBytes = new AtomicLong();
    /**
     * select发送字节数。
     */
    private final AtomicLong  selectTxBytes = new AtomicLong();
    /**
     * select接收字节数。
     */
    private final AtomicLong  selectRxBytes = new AtomicLong();
    /**
     * other发送字节数。
     */
    private final AtomicLong  otherTxBytes = new AtomicLong();
    /**
     * other接收字节数。
     */
    private final AtomicLong  otherRxBytes = new AtomicLong();


    public SchemaRunStats(long proxyId,long clusterId, long serverId, String database, String table) {
        this.proxyId = proxyId;
        this.clusterId = clusterId;
        this.serverId = serverId;
        this.database = database;
        this.table = table;
    }

    public SchemaRunStats() {
    }

    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
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

    /**
     * 检查是否报告给proxy。
     *
     * @return
     */
    public boolean checkReportProxy() {
        if (reportProxy) {
            reportProxy = false;
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查是否报告给schema。
     *
     * @return
     */
    public boolean checkReportSchema() {
        if (reportSchema) {
            reportSchema = false;
            return true;
        } else {
            return false;
        }
    }

    /**
     * 更新标记。
     */
    public void updateReportStatus() {
        this.reportProxy = true;
        this.reportSchema = true;
    }

    public long getInsertNum() {
        return insertNum.getAndSet( 0 );
    }

    public void addInsertNum(int insertNum) {
        this.insertNum.addAndGet( insertNum );
    }

    public long getUpdateNum() {
        return updateNum.getAndSet( 0 );
    }

    public void addUpdateNum(long updateNum) {
        this.updateNum.addAndGet( updateNum );
    }

    public long getDeleteNum() {
        return deleteNum.getAndSet( 0 );
    }

    public void addDeleteNum(long deleteNum) {
        this.deleteNum.addAndGet( deleteNum );
    }

    public long getSelectNum() {
        return selectNum.getAndSet( 0 );
    }

    public void addSelectNum(long selectNum) {
        this.selectNum.addAndGet( selectNum );
    }

    public long getOtherNum() {
        return otherNum.getAndSet( 0 );
    }

    public void addOtherNum(long otherNum) {
        this.otherNum.addAndGet( otherNum );
    }

    public long getInsertErrorNum() {
        return insertErrorNum.getAndSet( 0 );
    }

    public void addInsertErrorNum(long insertErrorNum) {
        this.insertErrorNum.addAndGet( insertErrorNum );
    }

    public long getUpdateErrorNum() {
        return updateErrorNum.getAndSet( 0 );
    }

    public void addUpdateErrorNum(long updateErrorNum) {
        this.updateErrorNum.addAndGet( updateErrorNum );
    }

    public long getDeleteErrorNum() {
        return deleteErrorNum.getAndSet( 0 );
    }

    public void addDeleteErrorNum(long deleteErrorNum) {
        this.deleteErrorNum.addAndGet( deleteErrorNum );
    }

    public long getSelectErrorNum() {
        return selectErrorNum.getAndSet( 0 );
    }

    public void addSelectErrorNum(long selectErrorNum) {
        this.selectErrorNum.addAndGet( selectErrorNum );
    }

    public long getOtherErrorNum() {
        return otherErrorNum.getAndSet( 0 );
    }

    public void addOtherErrorNum(long otherErrorNum) {
        this.otherErrorNum.addAndGet( otherErrorNum );
    }

    public long getInsertExeMillis() {
        return insertExeMillis.getAndSet( 0 );
    }

    public void addInsertExeMillis(long insertExeMillis) {
        this.insertExeMillis.addAndGet( insertExeMillis );
    }

    public long getUpdateExeMillis() {
        return updateExeMillis.getAndSet( 0 );
    }

    public void addUpdateExeMillis(long updateExeMillis) {
        this.updateExeMillis.addAndGet( updateExeMillis );
    }

    public long getDeleteExeMillis() {
        return deleteExeMillis.getAndSet( 0 );
    }

    public void addDeleteExeMillis(long deleteExeMillis) {
        this.deleteExeMillis.addAndGet( deleteExeMillis );
    }

    public long getSelectExeMillis() {
        return selectExeMillis.getAndSet( 0 );
    }

    public void addSelectExeMillis(long selectExeMillis) {
        this.selectExeMillis.addAndGet( selectExeMillis );
    }

    public long getOtherExeMillis() {
        return otherExeMillis.getAndSet( 0 );
    }

    public void addOtherExeMillis(long otherExeMillis) {
        this.otherExeMillis.addAndGet( otherExeMillis );
    }

    public long getInsertRowNum() {
        return insertRowNum.getAndSet( 0 );
    }

    public void addInsertRowNum(long insertRowNum) {
        this.insertRowNum.addAndGet( insertRowNum );
    }

    public long getUpdateRowNum() {
        return updateRowNum.getAndSet( 0 );
    }

    public void addUpdateRowNum(long updateRowNum) {
        this.updateRowNum.addAndGet( updateRowNum );
    }

    public long getDeleteRowNum() {
        return deleteRowNum.getAndSet( 0 );
    }

    public void addDeleteRowNum(long deleteRowNum) {
        this.deleteRowNum.addAndGet( deleteRowNum );
    }

    public long getSelectRowNum() {
        return selectRowNum.getAndSet( 0 );
    }

    public void addSelectRowNum(long selectRowNum) {
        this.selectRowNum.addAndGet( selectRowNum );
    }

    public long getOtherRowNum() {
        return otherRowNum.getAndSet( 0 );
    }

    public void addOtherRowNum(long otherRowNum) {
        this.otherRowNum.addAndGet( otherRowNum );
    }

    public long getInsertTxBytes() {
        return insertTxBytes.getAndSet( 0 );
    }

    public void addInsertTxBytes(long insertTxBytes) {
        this.insertTxBytes.addAndGet( insertTxBytes );
    }

    public long getInsertRxBytes() {
        return insertRxBytes.getAndSet( 0 );
    }

    public void addInsertRxBytes(long insertRxBytes) {
        this.insertRxBytes.addAndGet( insertRxBytes );
    }

    public long getUpdateTxBytes() {
        return updateTxBytes.getAndSet( 0 );
    }

    public void addUpdateTxBytes(long updateTxBytes) {
        this.updateTxBytes.addAndGet( updateTxBytes );
    }

    public long getUpdateRxBytes() {
        return updateRxBytes.getAndSet( 0 );
    }

    public void addUpdateRxBytes(long updateRxBytes) {
        this.updateRxBytes.addAndGet( updateRxBytes );
    }

    public long getDeleteTxBytes() {
        return deleteTxBytes.getAndSet( 0 );
    }

    public void addDeleteTxBytes(long deleteTxBytes) {
        this.deleteTxBytes.addAndGet( deleteTxBytes );
    }

    public long getDeleteRxBytes() {
        return deleteRxBytes.getAndSet( 0 );
    }

    public void addDeleteRxBytes(long deleteRxBytes) {
        this.deleteRxBytes.addAndGet( deleteRxBytes );
    }

    public long getSelectTxBytes() {
        return selectTxBytes.getAndSet( 0 );
    }

    public void addSelectTxBytes(long selectTxBytes) {
        this.selectTxBytes.addAndGet( selectTxBytes );
    }

    public long getSelectRxBytes() {
        return selectRxBytes.getAndSet( 0 );
    }

    public void addSelectRxBytes(long selectRxBytes) {
        this.selectRxBytes.addAndGet( selectRxBytes );
    }

    public long getOtherTxBytes() {
        return otherTxBytes.getAndSet( 0 );
    }

    public void addOtherTxBytes(long otherTxBytes) {
        this.otherTxBytes.addAndGet( otherTxBytes );
    }

    public long getOtherRxBytes() {
        return otherRxBytes.getAndSet( 0 );
    }

    public void addOtherRxBytes(long otherRxBytes) {
        this.otherRxBytes.addAndGet( otherRxBytes );
    }
}