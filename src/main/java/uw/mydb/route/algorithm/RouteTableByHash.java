package uw.mydb.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.util.ConsistentHash;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

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
     * 使用描述。
     */
    @Override
    public String description() {
        return """
                类型：分表算法
                说明：根据给定的long值，按照表数量hash分表。
                参数：key: routeList, value: mysqlCluster.database.table,mysqlCluster.database.table,...
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
