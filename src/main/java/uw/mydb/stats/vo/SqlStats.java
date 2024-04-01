package uw.mydb.stats.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.atomic.AtomicLong;

/**
 * sql统计信息。
 *
 * @author axeon
 */
public class SqlStats {


    /**
     * 读请求sql执行次数。
     */
    protected transient int sqlReadCount;

    /**
     * 写请求sql执行次数。
     */
    protected transient int sqlWriteCount;

    /**
     * 执行成功次数。
     */
    protected transient int exeSuccessCount;

    /**
     * 执行失败次数。
     */
    protected transient int exeFailureCount;

    /**
     * 数据行计数。
     */
    protected transient int dataRowsCount;

    /**
     * 受影响行计数。
     */
    protected transient int affectRowsCount;

    /**
     * 执行消耗时间。
     */
    protected transient long exeTime;

    /**
     * 发送字节数。
     */
    protected transient long sendBytes;

    /**
     * 接收字节数。
     */
    protected transient long recvBytes;

    public int getSqlReadCount() {
        return sqlReadCount;
    }

    public void addSqlReadCount(int sqlReadCount) {
        this.sqlReadCount += sqlReadCount;
    }

    public int getSqlWriteCount() {
        return sqlWriteCount;
    }

    public void addSqlWriteCount(int sqlWriteCount) {
        this.sqlWriteCount += sqlWriteCount;
    }

    public int getExeSuccessCount() {
        return exeSuccessCount;
    }

    public void addExeSuccessCount(int exeSuccessCount) {
        this.exeSuccessCount += exeSuccessCount;
    }

    public int getExeFailureCount() {
        return exeFailureCount;
    }

    public void addExeFailureCount(int exeFailureCount) {
        this.exeFailureCount += exeFailureCount;
    }

    public int getDataRowsCount() {
        return dataRowsCount;
    }

    public void addDataRowsCount(int dataRowsCount) {
        this.dataRowsCount += dataRowsCount;
    }

    public int getAffectRowsCount() {
        return affectRowsCount;
    }

    public void addAffectRowsCount(int affectRowsCount) {
        this.affectRowsCount += affectRowsCount;
    }

    public long getExeTime() {
        return exeTime;
    }

    public void addExeTime(long exeTime) {
        this.exeTime += exeTime;
    }

    public long getSendBytes() {
        return sendBytes;
    }

    public void addSendBytes(long sendBytes) {
        this.sendBytes += sendBytes;
    }

    public long getRecvBytes() {
        return recvBytes;
    }

    public void addRecvBytes(long recvBytes) {
        this.recvBytes += recvBytes;
    }
}
