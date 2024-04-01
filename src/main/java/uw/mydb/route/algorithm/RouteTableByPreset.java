package uw.mydb.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按照预定分表规则进行分表。
 * 参数：key=mysqlGroup.database.table
 */
public class RouteTableByPreset extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteTableByPreset.class );

    /**
     * 预设的表列表。
     */
    private Map<String, DataTable> routeMap = new HashMap<>();

    @Override
    public void config() {
        for (Map.Entry<String, String> kv : this.routeConfig.getRouteParamMap().entrySet()) {
            String[] data = kv.getValue().split( "\\." );
            if (data.length != 3) {
                logger.error( "参数配置错误！key:[{}], value:[{}]", kv.getKey(), kv.getValue() );
                continue;
            }
            routeMap.put( kv.getKey(), new DataTable( new DataNode( Long.parseLong( data[0] ), data[1] ), data[2] ) );

        }
    }


    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "预配置分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return """
                类型：分表算法
                说明：按照预定分表规则进行分表。
                参数：key: key, value: mysqlCluster.database.table
                """;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataTable route = routeMap.get( value );
        if (route == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        routeInfo = route.copy();
        return routeInfo;
    }

    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return new ArrayList<>( this.routeMap.values() );
    }

}
