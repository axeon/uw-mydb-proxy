package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.proxy.util.ConsistentHash;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于hash的分表算法。
 * 参数：routeList=mysqlGroup.database.table,mysqlGroup.database.table
 *
 * @author axeon
 */
public class RouteTableByHash extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteTableByHash.class );

    /**
     * 一致性hash对象。
     */
    private ConsistentHash<DataTable> consistentHash = null;

    /**
     * 预设的表列表。
     */
    private List<DataTable> routeInfos = new ArrayList<>();

    @Override
    public void config() {
        String routeList = this.routeConfig.getRouteParamMap().get( "routeList" );
        for (String route : routeList.split( "," )) {
            String[] data = route.split( "\\." );
            if (data.length != 3) {
                logger.error( "参数配置错误！route:[{}]", route );
                continue;
            }
            routeInfos.add(new DataTable( new DataNode(Long.parseLong( data[0] ), data[1]), data[2] ) );
        }
        consistentHash = new ConsistentHash<>( 128, routeInfos );
    }

    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "Hash分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return """
                根据给定的KEY值，按照表数量hash分表。
                参数说明:
                routeList: clusterId.database.table,clusterId.database.table,...
                """;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataTable route = consistentHash.get( value );
        if (route == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        return route.copy();
    }

    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return this.routeInfos;
    }

}
