package uw.mydb.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * mysql集群配置
 */
public class MysqlClusterConfig {
    public MysqlClusterConfig() {
    }

    public MysqlClusterConfig(long clusterId, String clusterName, int clusterType, int switchType, List<MysqlServerConfig> masters, List<MysqlServerConfig> slaves) {
        this.clusterId = clusterId;
        this.clusterName = clusterName;
        this.clusterType = clusterType;
        this.switchType = switchType;
        this.masters = masters;
        this.slaves = slaves;
    }

    /**
     * 集群ID。
     */
    private long clusterId;

    /**
     * 名称
     */
    private String clusterName;

    /**
     * 复制组类型
     */
    private int clusterType;

    /**
     * 切换类型
     */
    private int switchType;

    /**
     * 更新时间戳。
     */
    private long lastUpdate;

    /**
     * mysql主机列表
     */
    private List<MysqlServerConfig> masters = new ArrayList<>();

    /**
     * mysql从机列表
     */
    private List<MysqlServerConfig> slaves = new ArrayList<>();

    public long getClusterId() {
        return clusterId;
    }

    public void setClusterId(long clusterId) {
        this.clusterId = clusterId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public int getClusterType() {
        return clusterType;
    }

    public void setClusterType(int clusterType) {
        this.clusterType = clusterType;
    }

    public int getSwitchType() {
        return switchType;
    }

    public void setSwitchType(int switchType) {
        this.switchType = switchType;
    }

    public List<MysqlServerConfig> getMasters() {
        return masters;
    }

    public void setMasters(List<MysqlServerConfig> masters) {
        this.masters = masters;
    }

    public List<MysqlServerConfig> getSlaves() {
        return slaves;
    }

    public void setSlaves(List<MysqlServerConfig> slaves) {
        this.slaves = slaves;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
