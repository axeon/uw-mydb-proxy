package uw.mydb.proxy.route.algorithm;

import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;

import java.util.Map;

/**
 * 根据给定的key，来判断是否存在表，如果没有表，则动态自动创建以key为后缀的表。。
 * 需要在配置参数中配置mysqlGroup和database属性。
 *
 * @author axeon
 */
public class RouteTableByAutoKey extends RouteAlgorithm {

    /**
     * 基础数据节点。
     */
    private DataNode baseNode;

    @Override
    public void config() {
        Map<String, String> params = routeConfig.getRouteParamMap();
        baseNode = new DataNode( params.get( "baseNode" ) );
    }


    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "自动KEY分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return """
                根据给定的key，来判断是否存在表，如果没有表，则动态自动创建以key为后缀的表。
                参数说明:
                baseNode: clusterId.database 默认基础节点，可以不指定。
                """;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        String table = routeInfo.getTable() + "_" + value;
        routeInfo.setTable( table );
        if (!routeInfo.checkDataNode()){
            routeInfo.setDataNode( baseNode );
        }
        return routeInfo;
    }


}
