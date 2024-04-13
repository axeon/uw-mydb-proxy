package uw.mydb.stats.vo;

/**
 * 基于schema的sql统计信息。
 *
 * @author axeon
 */
public class SchemaRunStats extends SqlStats{

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

    public SchemaRunStats(long clusterId, long serverId, String database, String table) {
        this.clusterId = clusterId;
        this.serverId = serverId;
        this.database = database;
        this.table = table;
    }

    public SchemaRunStats() {
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
}