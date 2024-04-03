package uw.mydb.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.RouteConfig;
import uw.mydb.vo.TableConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由管理器。
 *
 * @author axeon
 */
public class RouteManager {

    private static final Logger logger = LoggerFactory.getLogger( RouteManager.class );

    /**
     * 算法实例的管理器
     */
    private static Map<Long, ArrayList<RouteAlgorithm>> routeAlgorithmMap = new ConcurrentHashMap<>();

    /**
     * 构造路由keyData。
     *
     * @param tableConfig
     * @return
     */
    public static RouteAlgorithm.RouteKeyData buildRouteKeyData(RouteAlgorithm.RouteKeyData routeKeyData, TableConfig tableConfig) {
        if (tableConfig == null) {
            return null;
        }
        RouteConfig routeConfig = MydbConfigService.getRouteConfig( tableConfig.getRouteId() );
        if (routeConfig == null) {
            return null;
        }
        //加载父级路由信息。
        if (routeConfig.getParentId() > 0) {
            RouteConfig parentRoute = MydbConfigService.getRouteConfig( routeConfig.getParentId() );
            if (parentRoute != null) {
                if (routeKeyData.getValue( parentRoute.getRouteKey() ) == null) {
                    routeKeyData.initKey( parentRoute.getRouteKey() );
                }
            }
        }
        //加载本级路由信息。
        if (routeKeyData.getValue( routeConfig.getRouteKey() ) == null) {
            routeKeyData.initKey( routeConfig.getRouteKey() );
        }
        return routeKeyData;
    }

    /**
     * 获得路由信息。
     *
     * @param tableConfig
     * @param routeInfo   默认匹配的路由
     * @param keyData
     * @return
     */
    public static RouteAlgorithm.RouteResultData calculate(TableConfig tableConfig, DataTable routeInfo, RouteAlgorithm.RouteKeyData keyData) throws RouteAlgorithm.RouteException {
        RouteAlgorithm.RouteResultData routeInfoData = new RouteAlgorithm.RouteResultData();
        //构造空路由配置。
        routeInfoData.setSingle( routeInfo );
        //获得路由算法列表。
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList( tableConfig.getRouteId() );
        if (routeAlgorithms == null) {
            return routeInfoData;
        }
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            RouteAlgorithm.RouteKeyValue routeKeyValue = keyData.getValue( routeAlgorithm.getRouteKey() );
            //优化一下calcType。
            routeKeyValue.guessType();
            if (routeKeyValue.getType() == RouteAlgorithm.RouteKeyValue.SINGLE) {
                routeInfo = routeAlgorithm.calculate( tableConfig, routeInfo, routeKeyValue.getValue1() );
                routeInfoData.setSingle( routeInfo );
            } else if (routeKeyValue.getType() == RouteAlgorithm.RouteKeyValue.RANGE) {
                Set<DataTable> set = new HashSet<>();
                if (routeInfoData.isSingle()) {
                    List<DataTable> list = routeAlgorithm.calculateRange( tableConfig, DataTable.newListWithRouteResult( routeInfoData.getRouteResult() ), routeKeyValue.getValue1(),
                            routeKeyValue.getValue2() );
                    set.addAll( list );
                } else {
                    for (DataTable dataTable : routeInfoData.getRouteResults()) {
                        List<DataTable> list = routeAlgorithm.calculateRange( tableConfig, DataTable.newListWithRouteResult( dataTable ), routeKeyValue.getValue1(), routeKeyValue.getValue2() );
                        set.addAll( list );
                    }
                }
                routeInfoData.setAll( set );
            } else if (routeKeyValue.getType() == RouteAlgorithm.RouteKeyValue.MULTI) {
                Set<DataTable> set = new HashSet<>();
                if (routeInfoData.isSingle()) {
                    Map<String, DataTable> map = routeAlgorithm.calculate( tableConfig, DataTable.newMapWithRouteResult( routeInfoData.getRouteResult() ), routeKeyValue.getValues() );
                    set.addAll( map.values() );
                } else {
                    for (DataTable dataTable : routeInfoData.getRouteResults()) {
                        Map<String, DataTable> map = routeAlgorithm.calculate( tableConfig, DataTable.newMapWithRouteResult( dataTable ), routeKeyValue.getValues() );
                        set.addAll( map.values() );
                    }
                }
                routeInfoData.setAll( set );
            } else {
                //此时说明参数没有匹配上。
                routeInfo = routeAlgorithm.getDefaultRoute( tableConfig, routeInfo );
                routeInfoData.setSingle( routeInfo );
            }
        }

        // 检查schema情况
        if (routeInfoData.isSingle()) {
            MydbConfigService.checkTableExists( tableConfig.getTableName(), routeInfoData.getRouteResult() );
        } else {
            for (DataTable dataTable : routeInfoData.getRouteResults()) {
                MydbConfigService.checkTableExists( tableConfig.getTableName(), dataTable );
            }
        }
        return routeInfoData;
    }

    /**
     * 获得所有表的信息。
     * 直接通过服务器端库表系统来查询。
     *
     * @param tableConfig
     * @return
     */
    public static List<DataTable> getAllRouteList(TableConfig tableConfig) throws RouteAlgorithm.RouteException {
        List<DataTable> routeInfo = new ArrayList<>();
        return routeInfo;
    }

    /**
     * 获得路由算法列表。
     *
     * @param routeId
     * @return
     */
    private static List<RouteAlgorithm> getRouteAlgorithmList(long routeId) {
        return routeAlgorithmMap.computeIfAbsent( routeId, key -> {
            ArrayList<RouteAlgorithm> algorithmList = new ArrayList<>();
            long loadRouteId = routeId;
            //重试最多10次。
            for (int i = 0; i < 10; i++) {
                if (loadRouteId < 1) {
                    return algorithmList;
                }
                RouteConfig routeConfig = MydbConfigService.getRouteConfig( loadRouteId );
                if (routeConfig == null) {
                    return algorithmList;
                }
                try {
                    Class clazz = Class.forName( routeConfig.getRouteAlgorithm() );
                    Object object = clazz.getDeclaredConstructor().newInstance();
                    if (object instanceof RouteAlgorithm algorithm) {
                        algorithm.init( routeConfig );
                        algorithm.config();
                        algorithmList.add( algorithm );
                    }
                } catch (Exception e) {
                    logger.error( "算法类加载失败！" + e.getMessage(), e );
                }
                //继续循环上级。
                if (routeConfig.getParentId() > 0) {
                    loadRouteId = routeConfig.getParentId();
                }
            }
            return algorithmList;
        } );
    }


}
