package uw.mydb.common.conf;

/**
 * 表配置。
 */
public class TableConfig {
    /**
     * 表名。
     */
    private String tableName;

    /**
     * 建表SQL。
     */
    private String tableSql;

    /**
     * 表信息。
     */
    private String tableDesc;

    /**
     * 路由设置。
     */
    private long routeId;

    /**
     * 基础节点。
     */
    private DataNode baseNode;

    /**
     * 匹配类型。
     * MATCH_FIX精确匹配：必须有匹配值，才能匹配，否则返回无法匹配。
     * MATCH_DEFAULT允许匹配有默认值：如果没有匹配值，可以匹配到默认值上。
     * MATCH_ALL允许匹配全量：如果没有匹配值，则全部匹配。
     */
    private int matchType;

    /**
     * 更新时间戳。
     */
    private long lastUpdate;

    public TableConfig() {
    }

    public TableConfig(String tableName, long baseCluster, String baseDatabase) {
        this.tableName = tableName;
        this.baseNode = new DataNode( baseCluster, baseDatabase );
    }

    public TableConfig(String tableName, String tableSql, String tableDesc, long baseCluster, String baseDatabase, long routeId, int matchType) {
        this.tableName = tableName;
        this.tableSql = tableSql;
        this.tableDesc = tableDesc;
        this.baseNode = new DataNode( baseCluster, baseDatabase );
        this.routeId = routeId;
        this.matchType = matchType;
    }

    public DataTable genDataTable() {
        return new DataTable( baseNode, tableName );
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableSql() {
        return tableSql;
    }

    public void setTableSql(String tableSql) {
        this.tableSql = tableSql;
    }

    public String getTableDesc() {
        return tableDesc;
    }

    public void setTableDesc(String tableDesc) {
        this.tableDesc = tableDesc;
    }

    public long getRouteId() {
        return routeId;
    }

    public void setRouteId(long routeId) {
        this.routeId = routeId;
    }

    public DataNode getBaseNode() {
        return baseNode;
    }

    public void setBaseNode(DataNode baseNode) {
        this.baseNode = baseNode;
    }

    public int getMatchType() {
        return matchType;
    }

    public void setMatchType(int matchType) {
        this.matchType = matchType;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
