package uw.mydb.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataNode;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

import java.util.Map;

/**
 * 特别为saas模式优化的一种分库方案。
 *
 * @author axeon
 */
public class RouteDatabaseBySaas extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteDatabaseBySaas.class );

    /**
     * 库名前缀
     */
    private String prefix = "saas_";

    /**
     * datanode映射表，key=prefix+value/库名
     */
    private Map<String, DataNode> dataNodeMap = null;

    @Override
    public void config() {
        //做好map映射表。
//        for (MydbConfig.DataNode saasNode :MydbConfigManager.getConfig().getSaasNodeMap().entrySet()){
//            dataNodeMap.put( saasNode.getSaasId(),new DataNode( saasNode.getClusterId(), saasNode.getDatabase() ) );
//        }
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
        DataNode dataNode = dataNodeMap.get(value);
        if (dataNode == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        routeInfo.setDataNode( dataNode );
        return routeInfo;
    }

}
