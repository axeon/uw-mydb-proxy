package uw.mydb.stats.vo;

import java.util.List;

/**
 * mysql链接统计信息。
 */
public class MysqlConnStats {

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
     * long数组为：clusterId,busyConnNum,idleConnNum。
     */
    private List<long[]> mysqlConnList;

    public MysqlConnStats(int mysqlNum, long mysqlBusyConnNum, long mysqlIdleConnNum, List<long[]> mysqlConnList) {
        this.mysqlNum = mysqlNum;
        this.mysqlBusyConnNum = mysqlBusyConnNum;
        this.mysqlIdleConnNum = mysqlIdleConnNum;
        this.mysqlConnList = mysqlConnList;
    }

    public MysqlConnStats() {
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
}
