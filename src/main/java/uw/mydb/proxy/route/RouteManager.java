package uw.mydb.proxy.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.constant.MydbRouteMatchMode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.RouteConfig;
import uw.mydb.common.conf.TableConfig;

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
     * 初始化路由keyData。
     *
     * @param tableConfig
     * @return
     */
    public static RouteAlgorithm.RouteData initRouteData(TableConfig tableConfig) {
        RouteAlgorithm.RouteData routeData = new RouteAlgorithm.RouteData();
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList( tableConfig.getRouteId() );
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            routeData.initKey( routeAlgorithm.getRouteKey() );
        }
        return routeData;
    }

    /**
     * 获得路由信息。
     *
     * @param tableConfig
     * @param routeData
     * @return
     */
    public static RouteAlgorithm.RouteResult calculate(TableConfig tableConfig, RouteAlgorithm.RouteData routeData) throws RouteAlgorithm.RouteException {
        RouteAlgorithm.RouteResult routeResult = new RouteAlgorithm.RouteResult();
        //获得路由算法列表。
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList( tableConfig.getRouteId() );
        if (routeAlgorithms == null) {
            return routeResult;
        }
        DataTable defaultRoute = tableConfig.genDataTable();
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            RouteAlgorithm.RouteValue routeValue = routeData.getValue( routeAlgorithm.getRouteKey() );
            if (routeValue == null || routeValue.isEmpty()) {
                //在路由名单里的，不指定参数，根据匹配类型确定转发。
                switch (MydbRouteMatchMode.findByValue( tableConfig.getMatchType() )) {
                    case MATCH_DEFAULT:
                        //此时说明参数没有匹配上。
                        defaultRoute = routeAlgorithm.getDefaultRoute( tableConfig, defaultRoute );
                        routeResult.setSingle( defaultRoute );
                        break;
                    case MATCH_ALL:
                        //匹配全部路由
                        routeResult.setAll( new HashSet<>( RouteManager.getAllRouteList( tableConfig ) ) );
                        break;
                    default:
                        //直接报错吧。
                        throw new RouteAlgorithm.RouteException( "Route can not fix match!" );
                }
            } else {
                routeValue.guessType();
                if (routeValue.getType() == RouteAlgorithm.RouteValue.SINGLE) {
                    defaultRoute = routeAlgorithm.calculate( tableConfig, defaultRoute, routeValue.getValueStart() );
                    routeResult.setSingle( defaultRoute );
                } else if (routeValue.getType() == RouteAlgorithm.RouteValue.RANGE) {
                    Set<DataTable> set = new LinkedHashSet<>();
                    if (routeResult.isSingle()) {
                        List<DataTable> list = routeAlgorithm.calculateRange( tableConfig, routeResult.getDataTable(), routeValue.getValueStart(), routeValue.getValueEnd() );
                        set.addAll( list );
                    } else {
                        for (DataTable dataTable : routeResult.getDataTables()) {
                            List<DataTable> list = routeAlgorithm.calculateRange( tableConfig, dataTable, routeValue.getValueStart(), routeValue.getValueEnd() );
                            set.addAll( list );
                        }
                    }
                    routeResult.setAll( set );
                } else if (routeValue.getType() == RouteAlgorithm.RouteValue.MULTI) {
                    Set<DataTable> set = new LinkedHashSet<>();
                    if (routeResult.isSingle()) {
                        Set<DataTable> dts = routeAlgorithm.calculate( tableConfig, routeResult.getDataTable(), routeValue.getValues() );
                        set.addAll( dts );
                    } else {
                        for (DataTable dataTable : routeResult.getDataTables()) {
                            Set<DataTable> dts = routeAlgorithm.calculate( tableConfig, dataTable, routeValue.getValues() );
                            set.addAll( dts );
                        }
                    }
                    routeResult.setAll( set );
                } else {
                    //此时说明参数没有匹配上。
                    defaultRoute = routeAlgorithm.getDefaultRoute( tableConfig, defaultRoute );
                    routeResult.setSingle( defaultRoute );
                }
            }
        }

        // 检查schema情况
        if (routeResult.isSingle()) {
            MydbProxyConfigService.checkTableExists( tableConfig.getTableName(), routeResult.getDataTable() );
        } else {
            for (DataTable dataTable : routeResult.getDataTables()) {
                MydbProxyConfigService.checkTableExists( tableConfig.getTableName(), dataTable );
            }
        }
        return routeResult;
    }

    /**
     * 获得所有表的信息。
     * 直接通过服务器端库表系统来查询。
     *
     * @param tableConfig
     * @return
     */
    public static List<DataTable> getAllRouteList(TableConfig tableConfig) throws RouteAlgorithm.RouteException {
        return MydbProxyConfigService.getTableListByPrefix( tableConfig.getTableName() + "_" );
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
                RouteConfig routeConfig = MydbProxyConfigService.getRouteConfig( loadRouteId );
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
                } else {
                    return algorithmList;
                }
            }
            return algorithmList;
        } );
    }


}
