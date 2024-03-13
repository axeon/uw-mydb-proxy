package uw.mydb.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据预设信息设置路由，此算法一般建立放在算法的最后，它会覆盖之前的配置。
 * params：key=routeKey，value="mysqlGroup.database"
 *
 * @author axeon
 */
public class RouteDatabaseByPreset extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteDatabaseByPreset.class );

    /**
     * 强行指定的参数范围。
     */
    private Map<String, DataNode> params = new HashMap<>();

    /**
     * 参数配置。
     */
    @Override
    public void config() {
        for (Map.Entry<String, String> kv : routeConfig.getRouteParamMap().entrySet()) {
            String[] data = kv.getValue().trim().split( "\\." );
            if (data.length != 2) {
                logger.error( "参数配置错误！key:[{}], value:[{}]", kv.getKey(), kv.getValue() );
                continue;
            }
            params.put( kv.getKey(), new DataNode( Long.parseLong( data[0] ), data[1] ) );

        }
    }

    /**
     * 使用描述。
     */
    @Override
    public String description() {
        return null;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataNode dataNode = params.get( value );
        if (dataNode == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        routeInfo.setDataNode( dataNode );
        return routeInfo;
    }

    /**
     * 获得全部路由。
     *
     * @param tableConfig
     * @param routeInfos
     * @return
     */
    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        for (Map.Entry<String, DataNode> kv : params.entrySet()) {
            DataNode dataNode = kv.getValue();
            DataTable routeInfo = new DataTable( dataNode, tableConfig.getTableName() );
            if (!routeInfos.contains( routeInfo )) {
                routeInfos.add( routeInfo );
            }
        }
        return super.getAllRouteList( tableConfig, routeInfos );
    }
}
