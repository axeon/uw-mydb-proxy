package uw.mydb.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.cache.FusionCache;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

/**
 * 特别为saas模式优化的一种分库方案。
 *
 * @author axeon
 */
public class RouteDatabaseBySaas extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteDatabaseBySaas.class );

    @Override
    public void config() {

    }

    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "saas分库路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return "特别为saas模式优化的一种分库方案。";
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataNode dataNode = FusionCache.get( DataNode.class, value );
        if (dataNode == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        routeInfo.setDataNode( dataNode );
        return routeInfo;
    }

}
