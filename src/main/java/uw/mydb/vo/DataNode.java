package uw.mydb.vo;

/**
 * 数据节点。
 */
public class DataNode {

    public DataNode(long clusterId, String database) {
        this.clusterId = clusterId;
        this.database = database;
    }

    public DataNode() {
    }

    /**
     * mysql集群。
     */
    private long clusterId;

    /**
     * mysql库名。
     */
    private String database;

    public long getClusterId() {
        return clusterId;
    }

    public void setClusterId(long clusterId) {
        this.clusterId = clusterId;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(this.clusterId).append('.').append(database).toString();
    }
}
