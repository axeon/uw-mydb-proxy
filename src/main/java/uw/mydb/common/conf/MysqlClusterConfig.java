package uw.mydb.common.conf;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * mysql集群配置
 */
public class MysqlClusterConfig {

    /**
     * 集群ID。
     */
    private long id;

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
     * mysql主机列表
     */
    private List<MysqlServerConfig> serverList = new ArrayList<>();

    /**
     * 主服务器索引位置。
     */
    @JsonIgnore
    private volatile int serverMasterWeightPos;

    /**
     * 所有服务器索引位置。
     */
    @JsonIgnore
    private volatile int serverAllWeightPos;

    /**
     * 主服务器列表。
     */
    @JsonIgnore
    private volatile List<MysqlServerConfig> serverMasterWeightList;

    /**
     * 所有服务器列表。
     */
    @JsonIgnore
    private volatile List<MysqlServerConfig> serverAllWeightList;


    public MysqlClusterConfig() {
    }

    public MysqlClusterConfig(long id, String clusterName, int clusterType, int switchType, List<MysqlServerConfig> serverList) {
        this.id = id;
        this.clusterName = clusterName;
        this.clusterType = clusterType;
        this.switchType = switchType;
        this.serverList = serverList;
    }

    /**
     * 获取服务器配置。
     *
     * @return
     */
    public MysqlServerConfig fetchServerConfig(boolean isMaster) {
        if (isMaster){
            return serverMasterWeightList.get( serverMasterWeightPos++ % serverMasterWeightList.size() );
        }else {
            return serverAllWeightList.get( serverAllWeightPos++ % serverAllWeightList.size() );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MysqlClusterConfig that = (MysqlClusterConfig) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    /**
     * 计算服务器权重。
     */
    public void initServerWeightList() {
        if (serverMasterWeightList == null || serverAllWeightList == null) {
            List<MysqlServerConfig> serverMasterWeightList = new ArrayList<>();
            List<MysqlServerConfig> serverAllWeightList = new ArrayList<>();
            for (MysqlServerConfig config : serverList) {
                if (config.getWeight()<1){
                    config.setWeight( 1 );
                }
                for (int i = 0; i < config.getWeight(); i++) {
                    serverMasterWeightList.add( config );
                    serverAllWeightList.add( config );
                }
            }
            Collections.shuffle( serverMasterWeightList );
            Collections.shuffle( serverAllWeightList );
            this.serverMasterWeightList = serverMasterWeightList;
            this.serverAllWeightList = serverAllWeightList;
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public List<MysqlServerConfig> getServerList() {
        return serverList;
    }

    public void setServerList(List<MysqlServerConfig> serverList) {
        this.serverList = serverList;
    }

}
