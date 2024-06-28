package uw.mydb.common.conf;


import java.util.Objects;

/**
 * 数据表。
 * 数据表通过DataNode和表名来确定唯一性。
 */
public class DataTable {

    /**
     * 数据节点。
     */
    private DataNode dataNode;

    /**
     * 表名。
     */
    private String table;

    public DataTable() {
    }

    public DataTable(DataNode dataNode, String table) {
        this.dataNode = dataNode;
        this.table = table;
    }

    /**
     * 只有表名的新实例。
     *
     * @param clusterId
     * @return
     */
    public static DataTable newDataWithClusterId(long clusterId) {
        return new DataTable( new DataNode( clusterId, null ), null );
    }


    /**
     * 只有表名的新实例。
     *
     * @param table
     * @return
     */
    public static DataTable newDataWithTable(String table) {
        return new DataTable( null, table );
    }


    /**
     * 检查是否合法。
     * 必须都非null值才合法。
     *
     * @return
     */
    public boolean checkValid() {
        return dataNode != null && dataNode.getClusterId() > 0 && dataNode.getDatabase() != null && table != null;
    }

    /**
     * 检查dataNode是否设置。
     * 必须都非null值才合法。
     *
     * @return
     */
    public boolean checkDataNode() {
        return dataNode != null && dataNode.getClusterId() > 0 && dataNode.getDatabase() != null;
    }

    /**
     * 复制一个RouteInfo。
     */
    public DataTable copy() {
        return new DataTable( dataNode, table );
    }

    @Override
    public int hashCode() {
        int result = dataNode != null ? dataNode.hashCode() : 0;
        result = 31 * result + (table != null ? table.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataTable dataTable = (DataTable) o;

        if (!Objects.equals( dataNode, dataTable.dataNode )) return false;
        return Objects.equals( table, dataTable.table );
    }

    @Override
    public String toString() {
        return new StringBuilder().append( dataNode.getClusterId() ).append( '.' ).append( dataNode.getDatabase() ).append( '.' ).append( table ).toString();
    }

    /**
     * 生成sql标识符。
     *
     * @return
     */
    public String genSqlIdentity() {
        if (dataNode == null) {
            return table;
        } else {
            return dataNode.getDatabase() + '.' + table;
        }
    }

    public DataNode getDataNode() {
        return dataNode;
    }

    public void setDataNode(DataNode dataNode) {
        this.dataNode = dataNode;
    }

    public long getClusterId() {
        return dataNode.getClusterId();
    }

    public String getDatabase() {
        return dataNode.getDatabase();
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}
