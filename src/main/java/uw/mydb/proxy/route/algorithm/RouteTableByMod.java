package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据给定的long值，按照表数量直接mod分表。
 * 参数：routeList=mysqlGroup.database.table,mysqlGroup.database.table
 *
 * @author axeon
 */
public class RouteTableByMod extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(RouteTableByMod.class);


    /**
     * 预设的表列表。
     */
    private List<DataTable> routeInfos = new ArrayList<>();

    @Override
    public void config() {
        String routeList = this.routeConfig.getRouteParamMap().get("routeList");
        for (String route : routeList.split(",")) {
            String[] data = route.split("\\.");
            if (data.length != 3) {
                logger.error("参数配置错误！route:[{}]", route);
                continue;
            }
            routeInfos.add(new DataTable( new DataNode(Long.parseLong( data[0] ), data[1]), data[2] ) );
        }
    }


    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "Mod分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return """
                根据给定的long值，按照表数量直接mod分表。
                参数说明:
                routeList: clusterId.database.table,clusterId.database.table,...
                """;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        long longValue = -1L;

        try {
            longValue = Long.parseLong(value);
        } catch (Exception e) {
            throw new RouteException("calculate计算失败，参数值[" + value + "]错误！");
        }

        longValue = Math.abs(longValue);
        routeInfo = routeInfos.get((int) (longValue % routeInfos.size())).copy();
        return routeInfo;
    }


    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return this.routeInfos;
    }


}
