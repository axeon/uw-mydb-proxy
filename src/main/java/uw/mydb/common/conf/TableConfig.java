package uw.mydb.common.conf;

/**
 * 逻辑表分片配置实体（proxy 与 center 共享），描述一张逻辑表如何路由到物理分表。
 * <p>
 * 关键字段：
 * <ul>
 *   <li>{@link #baseNode}：基础数据节点（clusterId + database），未配置路由或未命中时的兜底目标。</li>
 *   <li>{@link #routeId}：路由算法配置 ID，&gt;0 表示启用分片路由；=0 表示不分片，直接走 baseNode。</li>
 *   <li>{@link #matchType}：路由匹配模式，见 {@link uw.mydb.proxy.constant.MydbRouteMatchMode}。</li>
 *   <li>{@link #tableSql}：建表 SQL，动态建表（ensureTableExists）时使用。</li>
 * </ul>
 */
public class TableConfig {
    /**
     * 逻辑表名（如 t_user）。
     */
    private String tableName;

    /**
     * 建表 SQL（动态分表时执行）。
     */
    private String tableSql;

    /**
     * 表描述信息（管理后台展示用）。
     */
    private String tableDesc;

    /**
     * 路由算法配置 ID。0 = 不分片；&gt;0 = 启用分片路由。
     */
    private long routeId;

    /**
     * 基础数据节点（未命中路由时的兜底 clusterId + database）。
     */
    private DataNode baseNode;

    /**
     * 路由匹配模式（{@link uw.mydb.proxy.constant.MydbRouteMatchMode}）：
     * <ul>
     *   <li>MATCH_FIX（精确匹配）：必须有匹配值才能匹配，否则返回无法匹配。</li>
     *   <li>MATCH_DEFAULT（默认匹配）：无匹配值时回落到默认值。</li>
     *   <li>MATCH_ALL（全量匹配）：无匹配值时路由到全部分片。</li>
     * </ul>
     */
    private int matchType;

    /**
     * 配置最后更新时间戳（毫秒）。
     */
    private long lastUpdate;

    /**
     * 默认构造器（反序列化用）。
     */
    public TableConfig() {
    }

    /**
     * 构造不分片表（仅 baseNode）。
     *
     * @param tableName    表名
     * @param baseCluster  基础集群 ID
     * @param baseDatabase 基础 database
     */
    public TableConfig(String tableName, long baseCluster, String baseDatabase) {
        this.tableName = tableName;
        this.baseNode = new DataNode( baseCluster, baseDatabase );
    }

    /**
     * 全参构造器。
     *
     * @param tableName    表名
     * @param tableSql     建表 SQL
     * @param tableDesc    表描述
     * @param baseCluster  基础集群 ID
     * @param baseDatabase 基础 database
     * @param routeId      路由 ID
     * @param matchType    匹配模式
     */
    public TableConfig(String tableName, String tableSql, String tableDesc, long baseCluster, String baseDatabase, long routeId, int matchType) {
        this.tableName = tableName;
        this.tableSql = tableSql;
        this.tableDesc = tableDesc;
        this.baseNode = new DataNode( baseCluster, baseDatabase );
        this.routeId = routeId;
        this.matchType = matchType;
    }

    /**
     * 基于当前 baseNode 与 tableName 构造一个 {@link DataTable}，用于未命中路由时的兜底目标。
     *
     * @return 兜底 DataTable
     */
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
