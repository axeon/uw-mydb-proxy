package uw.mydb.vo;


import java.util.*;

/**
 * 数据表。
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

    public DataTable(DataNode dataNode, String table) {
        this.dataNode = dataNode;
        this.table = table;
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
     * new一个只有单个RouteInfo的Map。
     *
     * @param dataTable
     * @return
     */
    public static Map<String, DataTable> newMapWithRouteResult(DataTable dataTable) {
        HashMap<String, DataTable> map = new HashMap<>();
        map.put( "", dataTable );
        return map;
    }

    /**
     * new一个只有单个RouteInfo的List。
     *
     * @param dataTable
     * @return
     */
    public static List<DataTable> newListWithRouteResult(DataTable dataTable) {
        List<DataTable> list = new ArrayList<>();
        list.add( dataTable );
        return list;
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
