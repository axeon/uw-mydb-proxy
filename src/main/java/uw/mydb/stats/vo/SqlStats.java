package uw.mydb.stats.vo;

/**
 * sql统计信息。
 *
 * @author axeon
 */
public class SqlStats {

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
    protected transient int insertNum;

    protected transient int updateNum;

    protected transient int deleteNum;

    protected transient int selectNum;

    protected transient int otherNum;

    protected transient int insertErrorNum;

    protected transient int updateErrorNum;

    protected transient int deleteErrorNum;

    protected transient int selectErrorNum;

    protected transient int otherErrorNum;

    protected transient long insertExeMillis;

    protected transient long updateExeMillis;

    protected transient long deleteExeMillis;

    protected transient long selectExeMillis;

    protected transient long otherExeMillis;

    protected transient long insertRowNum;

    protected transient long updateRowNum;

    protected transient long deleteRowNum;

    protected transient long selectRowNum;

    protected transient long otherRowNum;

    protected transient long insertTxBytes;

    protected transient long insertRxBytes;

    protected transient long updateTxBytes;

    protected transient long updateRxBytes;

    protected transient long deleteTxBytes;

    protected transient long deleteRxBytes;

    protected transient long selectTxBytes;

    protected transient long selectRxBytes;

    protected transient long otherTxBytes;

    protected transient long otherRxBytes;


    public int getInsertNum() {
        return insertNum;
    }

    public void setInsertNum(int insertNum) {
        this.insertNum = insertNum;
    }

    public int getUpdateNum() {
        return updateNum;
    }

    public void setUpdateNum(int updateNum) {
        this.updateNum = updateNum;
    }

    public int getDeleteNum() {
        return deleteNum;
    }

    public void setDeleteNum(int deleteNum) {
        this.deleteNum = deleteNum;
    }

    public int getSelectNum() {
        return selectNum;
    }

    public void setSelectNum(int selectNum) {
        this.selectNum = selectNum;
    }

    public int getOtherNum() {
        return otherNum;
    }

    public void setOtherNum(int otherNum) {
        this.otherNum = otherNum;
    }

    public int getInsertErrorNum() {
        return insertErrorNum;
    }

    public void setInsertErrorNum(int insertErrorNum) {
        this.insertErrorNum = insertErrorNum;
    }

    public int getUpdateErrorNum() {
        return updateErrorNum;
    }

    public void setUpdateErrorNum(int updateErrorNum) {
        this.updateErrorNum = updateErrorNum;
    }

    public int getDeleteErrorNum() {
        return deleteErrorNum;
    }

    public void setDeleteErrorNum(int deleteErrorNum) {
        this.deleteErrorNum = deleteErrorNum;
    }

    public int getSelectErrorNum() {
        return selectErrorNum;
    }

    public void setSelectErrorNum(int selectErrorNum) {
        this.selectErrorNum = selectErrorNum;
    }

    public int getOtherErrorNum() {
        return otherErrorNum;
    }

    public void setOtherErrorNum(int otherErrorNum) {
        this.otherErrorNum = otherErrorNum;
    }

    public long getInsertExeMillis() {
        return insertExeMillis;
    }

    public void setInsertExeMillis(long insertExeMillis) {
        this.insertExeMillis = insertExeMillis;
    }

    public long getUpdateExeMillis() {
        return updateExeMillis;
    }

    public void setUpdateExeMillis(long updateExeMillis) {
        this.updateExeMillis = updateExeMillis;
    }

    public long getDeleteExeMillis() {
        return deleteExeMillis;
    }

    public void setDeleteExeMillis(long deleteExeMillis) {
        this.deleteExeMillis = deleteExeMillis;
    }

    public long getSelectExeMillis() {
        return selectExeMillis;
    }

    public void setSelectExeMillis(long selectExeMillis) {
        this.selectExeMillis = selectExeMillis;
    }

    public long getOtherExeMillis() {
        return otherExeMillis;
    }

    public void setOtherExeMillis(long otherExeMillis) {
        this.otherExeMillis = otherExeMillis;
    }

    public long getInsertRowNum() {
        return insertRowNum;
    }

    public void setInsertRowNum(long insertRowNum) {
        this.insertRowNum = insertRowNum;
    }

    public long getUpdateRowNum() {
        return updateRowNum;
    }

    public void setUpdateRowNum(long updateRowNum) {
        this.updateRowNum = updateRowNum;
    }

    public long getDeleteRowNum() {
        return deleteRowNum;
    }

    public void setDeleteRowNum(long deleteRowNum) {
        this.deleteRowNum = deleteRowNum;
    }

    public long getSelectRowNum() {
        return selectRowNum;
    }

    public void setSelectRowNum(long selectRowNum) {
        this.selectRowNum = selectRowNum;
    }

    public long getOtherRowNum() {
        return otherRowNum;
    }

    public void setOtherRowNum(long otherRowNum) {
        this.otherRowNum = otherRowNum;
    }

    public long getInsertTxBytes() {
        return insertTxBytes;
    }

    public void setInsertTxBytes(long insertTxBytes) {
        this.insertTxBytes = insertTxBytes;
    }

    public long getInsertRxBytes() {
        return insertRxBytes;
    }

    public void setInsertRxBytes(long insertRxBytes) {
        this.insertRxBytes = insertRxBytes;
    }

    public long getUpdateTxBytes() {
        return updateTxBytes;
    }

    public void setUpdateTxBytes(long updateTxBytes) {
        this.updateTxBytes = updateTxBytes;
    }

    public long getUpdateRxBytes() {
        return updateRxBytes;
    }

    public void setUpdateRxBytes(long updateRxBytes) {
        this.updateRxBytes = updateRxBytes;
    }

    public long getDeleteTxBytes() {
        return deleteTxBytes;
    }

    public void setDeleteTxBytes(long deleteTxBytes) {
        this.deleteTxBytes = deleteTxBytes;
    }

    public long getDeleteRxBytes() {
        return deleteRxBytes;
    }

    public void setDeleteRxBytes(long deleteRxBytes) {
        this.deleteRxBytes = deleteRxBytes;
    }

    public long getSelectTxBytes() {
        return selectTxBytes;
    }

    public void setSelectTxBytes(long selectTxBytes) {
        this.selectTxBytes = selectTxBytes;
    }

    public long getSelectRxBytes() {
        return selectRxBytes;
    }

    public void setSelectRxBytes(long selectRxBytes) {
        this.selectRxBytes = selectRxBytes;
    }

    public long getOtherTxBytes() {
        return otherTxBytes;
    }

    public void setOtherTxBytes(long otherTxBytes) {
        this.otherTxBytes = otherTxBytes;
    }

    public long getOtherRxBytes() {
        return otherRxBytes;
    }

    public void setOtherRxBytes(long otherRxBytes) {
        this.otherRxBytes = otherRxBytes;
    }
}