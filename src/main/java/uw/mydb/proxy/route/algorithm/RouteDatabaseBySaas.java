package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;

import java.util.List;

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
        List<DataNode> dataNodeList = MydbProxyConfigService.getSaasNode( value );
        if (dataNodeList == null || dataNodeList.size() == 0) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]无法找到对应路由！" );
        }
        routeInfo.setDataNode( dataNodeList.get( 0 ) );
        return routeInfo;
    }

}
