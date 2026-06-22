package uw.mydb.common.conf;

import java.util.Objects;

/**
 * 数据节点对象。
 * 数据节点通过服务器集群和库来确定唯一性。
 */
public class DataNode {

    /**
     * mysql集群。
     */
    private long clusterId;

    /**
     * mysql库名。
     */
    private String database;

    public DataNode() {
    }

    public DataNode(long clusterId, String database) {
        this.clusterId = clusterId;
        this.database = database;
    }

    /**
     * 通过集群ID.数据库名来确定唯一值。
     *
     * @param dataNodeKey
     */
    public DataNode(String dataNodeKey) {
        if (dataNodeKey != null) {
            int splitPos = dataNodeKey.indexOf( '.' );
            if (splitPos > -1) {
                clusterId = Long.parseLong( dataNodeKey.substring( 0, splitPos ) );
                database = dataNodeKey.substring( splitPos + 1 );
            }
        }
    }


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

    /**
     * 基于clusterId+database判断相等，保证DataTable在HashSet/LinkedHashSet中能正确去重。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataNode)) return false;
        DataNode dataNode = (DataNode) o;
        return clusterId == dataNode.clusterId && Objects.equals(database, dataNode.database);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, database);
    }

    @Override
    public String toString() {
        return new StringBuilder().append( this.clusterId ).append( '.' ).append( database ).toString();
    }
}
