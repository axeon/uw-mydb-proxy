package uw.mydb.route.algorithm;

import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

/**
 * 根据给定的key，来判断是否存在表，如果没有表，则动态自动创建以key为后缀的表。。
 * 需要在配置参数中配置mysqlGroup和database属性。
 *
 * @author axeon
 */
public class RouteTableByAutoKey extends RouteAlgorithm {

    /**
     * 数据节点
     */
    private DataNode dataNode;

    @Override
    public void config() {
        dataNode = new DataNode(Long.parseLong( this.routeConfig.getRouteParamMap().get( "clusterId" )),this.routeConfig.getRouteParamMap().get( "database" ) );
    }


    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "关键字分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return """
                类型：分表算法
                说明：根据给定的key，来判断是否存在表，如果没有表，则动态自动创建以key为后缀的表。
                参数：需要在配置参数中配置"mysqlCluster"和"database"属性。
                """;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        String table =  routeInfo.getTable() + "_" +value;
        routeInfo.setTable( table );
        routeInfo.setDataNode( dataNode );
        return routeInfo;
    }


}
