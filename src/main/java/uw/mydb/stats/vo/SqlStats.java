package uw.mydb.stats.vo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * sql统计信息。
 *
 * @author axeon
 */
public class SqlStats {

    /**
     * insert计数。
     */
    protected AtomicInteger insertNum = new AtomicInteger();

    /**
     * update计数。
     */
    protected AtomicInteger updateNum = new AtomicInteger();

    /**
     * delete计数。
     */
    protected AtomicInteger deleteNum = new AtomicInteger();

    /**
     * select计数。
     */
    protected AtomicInteger selectNum = new AtomicInteger();

    /**
     * other计数。
     */
    protected AtomicInteger otherNum = new AtomicInteger();

    /**
     * insert错误计数。
     */
    protected AtomicInteger insertErrorNum = new AtomicInteger();

    /**
     * update错误计数。
     */
    protected AtomicInteger updateErrorNum = new AtomicInteger();

    /**
     * delete错误计数。
     */
    protected AtomicInteger deleteErrorNum = new AtomicInteger();

    /**
     * select错误计数。
     */
    protected AtomicInteger selectErrorNum = new AtomicInteger();

    /**
     * other错误计数。
     */
    protected AtomicInteger otherErrorNum = new AtomicInteger();

    /**
     * insert执行耗时毫秒数。
     */
    protected AtomicLong insertExeMillis = new AtomicLong();

    /**
     * update执行耗时毫秒数。
     */
    protected AtomicLong updateExeMillis = new AtomicLong();

    /**
     * delete执行耗时毫秒数。
     */
    protected AtomicLong deleteExeMillis = new AtomicLong();

    /**
     * select执行耗时毫秒数。
     */
    protected AtomicLong selectExeMillis = new AtomicLong();

    /**
     * other执行耗时毫秒数。
     */
    protected AtomicLong otherExeMillis = new AtomicLong();

    /**
     * insert影响行数。
     */
    protected AtomicLong insertRowNum = new AtomicLong();

    /**
     * insert影响行数。
     */
    protected AtomicLong updateRowNum = new AtomicLong();

    /**
     * insert影响行数。
     */
    protected AtomicLong deleteRowNum = new AtomicLong();

    /**
     * insert影响行数。
     */
    protected AtomicLong selectRowNum = new AtomicLong();

    /**
     * insert影响行数。
     */
    protected AtomicLong otherRowNum = new AtomicLong();

    /**
     * insert发送字节数。
     */
    protected AtomicLong insertTxBytes = new AtomicLong();

    /**
     * insert接收字节数。
     */
    protected AtomicLong insertRxBytes = new AtomicLong();

    /**
     * update发送字节数。
     */
    protected AtomicLong updateTxBytes = new AtomicLong();

    /**
     * update接收字节数。
     */
    protected AtomicLong updateRxBytes = new AtomicLong();

    /**
     * delete发送字节数。
     */
    protected AtomicLong deleteTxBytes = new AtomicLong();

    /**
     * delete接收字节数。
     */
    protected AtomicLong deleteRxBytes = new AtomicLong();

    /**
     * select发送字节数。
     */
    protected AtomicLong selectTxBytes = new AtomicLong();

    /**
     * select接收字节数。
     */
    protected AtomicLong selectRxBytes = new AtomicLong();

    /**
     * other发送字节数。
     */
    protected AtomicLong otherTxBytes = new AtomicLong();

    /**
     * other接收字节数。
     */
    protected AtomicLong otherRxBytes = new AtomicLong();


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