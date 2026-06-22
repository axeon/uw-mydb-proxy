package uw.mydb.proxy.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.RouteConfig;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.constant.MydbRouteMatchMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由管理器：算法链装配、路由计算分发的中心入口。
 *
 * <h2>核心职责</h2>
 * <ol>
 *   <li>按 routeId 加载并缓存算法链（最多 3 个算法，由 {@link RouteConfig#getParentId()} 串接）。</li>
 *   <li>把 SQL 解析出的分片键填充进 {@link RouteAlgorithm.RouteData}，再驱动算法链逐个计算。</li>
 *   <li>对每段算法，依据 {@link RouteAlgorithm.RouteValue} 的 type 分发到 SINGLE/RANGE/MULTI 分支，
 *       或在值为空时根据 {@code tableConfig.matchType} 走 MATCH_DEFAULT/MATCH_ALL 兜底。</li>
 *   <li>最终对结果路由做库表存在性校验（{@code MydbProxyConfigService.ensureTableExists}）。</li>
 * </ol>
 *
 * <h2>路由计算流程</h2>
 * <pre>
 * {@link #calculate}:
 *   1. routeAlgorithms = getRouteAlgorithmList(routeId)
 *   2. routeResult 初始化为 defaultRoute（tableConfig.genDataTable()）
 *   3. for each algorithm in chain:
 *        routeValue = routeData.getValue(algorithm.routeKey)
 *        if (routeValue 空):
 *           switch matchType:
 *             MATCH_DEFAULT -> getDefaultRoute() 单条
 *             MATCH_ALL     -> 全表扫描，多条
 *             其它          -> 抛 RouteException
 *        else:
 *           guessType()
 *           SINGLE -> calculate() 单条
 *           RANGE  -> 对已有结果集合做笛卡尔 calculateRange()
 *           MULTI  -> 对已有结果集合做笛卡尔 calculate(values)
 *   4. ensureTableExists 校验
 * </pre>
 *
 * <h2>线程安全</h2>
 * 类为全静态。{@link #routeAlgorithmMap} 使用 {@link ConcurrentHashMap}，算法实例在
 * {@link #getRouteAlgorithmList} 中通过 putIfAbsent 发布；发布前 algorithm.config() 已完成，
 * 且算法实例字段后续只读，因此对并发读安全。缓存失效通过
 * {@link #invalidateRouteAlgorithm(long)} / {@link #invalidateAllRouteAlgorithm()} 显式触发。
 *
 * @author axeon
 */
public class RouteManager {

    private static final Logger logger = LoggerFactory.getLogger( RouteManager.class );

    /**
     * routeId -> 算法链（按 parent 链顺序）的缓存。
     * <p>value 是 {@code ArrayList}，发布后不再 mutate，可安全并发读。
     * key 为 {@link TableConfig#getRouteId()}。</p>
     */
    private static Map<Long, ArrayList<RouteAlgorithm>> routeAlgorithmMap = new ConcurrentHashMap<>();

    /**
     * 根据逻辑表的 routeId 初始化一个空的 {@link RouteAlgorithm.RouteData}，预先填充好算法链上每个算法的 routeKey。
     * <p>调用方拿到 RouteData 后，再根据 SQL 解析结果调用 {@code putValue/putRangeStart/putValues}
     * 把实际值写进对应槽位。</p>
     *
     * @param tableConfig 逻辑表配置，非 null。其 routeId 决定加载哪条算法链
     * @return 预填充了 routeKey 的 RouteData；若算法链为空则返回所有槽位 null 的 RouteData
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
     * 主路由计算入口：驱动算法链对 RouteData 做计算，返回 {@link RouteAlgorithm.RouteResult}。
     *
     * <p>对每个算法，根据 {@link RouteAlgorithm.RouteValue} 的 type 与 {@code tableConfig.matchType}
     * 选择下列分支之一：
     * <ul>
     *   <li>值为空 + MATCH_DEFAULT：调用 {@link RouteAlgorithm#getDefaultRoute}，结果单条</li>
     *   <li>值为空 + MATCH_ALL：调用 {@link #getAllRouteList}，结果多条</li>
     *   <li>值为空 + 其它 matchType：抛 RouteException("Route can not fix match!")</li>
     *   <li>SINGLE：调用 {@link RouteAlgorithm#calculate(TableConfig, DataTable, String)}，单条</li>
     *   <li>RANGE：对当前结果做笛卡尔 {@link RouteAlgorithm#calculateRange}，多条</li>
     *   <li>MULTI：对当前结果做笛卡尔 {@link RouteAlgorithm#calculate(TableConfig, DataTable, List)}，多条</li>
     *   <li>type=NULL 等其它情况：退化为 getDefaultRoute 单条</li>
     * </ul>
     * 笛卡尔展开：若上一段输出多条，本段对每个父路由都计算一遍再合并去重。</p>
     *
     * @param tableConfig 逻辑表配置，非 null。其 routeId 决定算法链、matchType 决定空值兜底
     * @param routeData   分片键容器，非 null。一般由 {@link #initRouteData} 创建并由 SQL 解析器填充
     * @return 路由结果，非 null；可能单条也可能多条，可能 0 命中（算法链为空时返回默认路由的 RouteResult）
     * @throws RouteAlgorithm.RouteException 当 matchType 配置错误、MATCH_ALL 取不到列表、
     *         算法显式抛出（参数错误、范围过大等）时抛出
     */
    public static RouteAlgorithm.RouteResult calculate(TableConfig tableConfig, RouteAlgorithm.RouteData routeData) throws RouteAlgorithm.RouteException {
        RouteAlgorithm.RouteResult routeResult = new RouteAlgorithm.RouteResult();
        //获取路由算法列表。
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList( tableConfig.getRouteId() );
        //算法列表为空（未配置或加载失败），直接返回空结果，由上层走默认路由。
        if (routeAlgorithms == null || routeAlgorithms.isEmpty()) {
            return routeResult;
        }
        DataTable defaultRoute = tableConfig.genDataTable();
        //初始化routeResult为默认路由，避免首个算法为RANGE/MULTI时getDataTable()返回null。
        routeResult.setSingle( defaultRoute );
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            RouteAlgorithm.RouteValue routeValue = routeData.getValue( routeAlgorithm.getRouteKey() );
            if (routeValue == null || routeValue.isEmpty()) {
                //在路由名单里的，不指定参数，根据匹配类型确定转发。
                MydbRouteMatchMode matchMode = MydbRouteMatchMode.findByValue( tableConfig.getMatchType() );
                if (matchMode == null) {
                    throw new RouteAlgorithm.RouteException( "Route matchType配置错误！matchType=" + tableConfig.getMatchType() );
                }
                switch (matchMode) {
                    case MATCH_DEFAULT:
                        //此时说明参数没有匹配上。
                        defaultRoute = routeAlgorithm.getDefaultRoute( tableConfig, defaultRoute );
                        routeResult.setSingle( defaultRoute );
                        break;
                    case MATCH_ALL:
                        //匹配全部路由
                        List<DataTable> allRouteList = RouteManager.getAllRouteList( tableConfig );
                        if (allRouteList == null || allRouteList.isEmpty()) {
                            throw new RouteAlgorithm.RouteException( "Route MATCH_ALL获取路由列表为空！" );
                        }
                        routeResult.setAll( new HashSet<>( allRouteList ) );
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
                        DataTable baseTable = routeResult.getDataTable() != null ? routeResult.getDataTable() : defaultRoute;
                        List<DataTable> list = routeAlgorithm.calculateRange( tableConfig, baseTable, routeValue.getValueStart(), routeValue.getValueEnd() );
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
                        DataTable baseTable = routeResult.getDataTable() != null ? routeResult.getDataTable() : defaultRoute;
                        Set<DataTable> dts = routeAlgorithm.calculate( tableConfig, baseTable, routeValue.getValues() );
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
            MydbProxyConfigService.ensureTableExists( tableConfig.getTableName(), routeResult.getDataTable() );
        } else {
            for (DataTable dataTable : routeResult.getDataTables()) {
                MydbProxyConfigService.ensureTableExists( tableConfig.getTableName(), dataTable );
            }
        }
        return routeResult;
    }

    /**
     * 取回逻辑表的全部物理分表，用于 MATCH_ALL 全表扫描兜底。
     * 直接通过管理端按表名前缀查询实际存在的库表，不依赖算法枚举。
     *
     * @param tableConfig 逻辑表配置，非 null
     * @return 命中的物理表列表；可能为空
     * @throws RouteAlgorithm.RouteException 当底层查询失败时抛出
     */
    public static List<DataTable> getAllRouteList(TableConfig tableConfig) throws RouteAlgorithm.RouteException {
        return MydbProxyConfigService.getTableListByPrefix( tableConfig.getTableName() + "_" );
    }

    /**
     * 按 routeId 加载并缓存算法链。
     * <p>通过 {@link RouteConfig#getParentId()} 递归向上最多 10 层，反射实例化每个算法类，
     * 依次 init() + config()，按链顺序存入 ArrayList。算法链次序：当前 routeId 在前，
     * parentId 在后，对应执行顺序为"先分库再分表"。</p>
     *
     * <p>缓存策略：用 putIfAbsent 发布，加载失败（空链）不缓存，避免配置暂时不可用时永久缓存空结果。
     * 算法类加载异常会记录 ERROR 日志并跳过该算法，不影响链上其它算法。</p>
     *
     * @param routeId 逻辑表对应的根 routeId
     * @return 算法链；可能为 null（routeId 配置不存在，或全部加载失败且未缓存）
     */
    private static List<RouteAlgorithm> getRouteAlgorithmList(long routeId) {
        List<RouteAlgorithm> existing = routeAlgorithmMap.get( routeId );
        if (existing != null) {
            return existing;
        }
        // 在 computeIfAbsent 外部完成加载，避免阻塞 ConcurrentHashMap 分段锁
        ArrayList<RouteAlgorithm> algorithmList = new ArrayList<>();
        long loadRouteId = routeId;
        for (int i = 0; i < 10; i++) {
            RouteConfig routeConfig = MydbProxyConfigService.getRouteConfig( loadRouteId );
            if (routeConfig == null) {
                break;
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
            if (routeConfig.getParentId() > 0) {
                loadRouteId = routeConfig.getParentId();
            } else {
                break;
            }
        }
        //加载失败（空列表）不缓存，避免配置暂时不可用时永久缓存空结果。
        if (!algorithmList.isEmpty()) {
            routeAlgorithmMap.putIfAbsent( routeId, algorithmList );
        }
        return routeAlgorithmMap.get( routeId );
    }

    /**
     * 作废指定 routeId 的算法链缓存。
     * 在路由配置（routeParamMap/routeKey/parentId/算法类名）发生变更时由配置变更监听器调用，
     * 下次 {@link #calculate} 会重新走 {@link #getRouteAlgorithmList} 加载。
     *
     * @param routeId 要失效的 routeId
     */
    public static void invalidateRouteAlgorithm(long routeId) {
        routeAlgorithmMap.remove( routeId );
    }

    /**
     * 作废全部算法链缓存。
     * 一般用于全量配置重载/批量变更场景，调用后所有 routeId 都会在下次访问时重新加载。
     */
    public static void invalidateAllRouteAlgorithm() {
        routeAlgorithmMap.clear();
    }


}
