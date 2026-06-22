package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预配置分表路由：按 routeKey 的值精确映射到指定的 (clusterId, database, table) 三元组。
 *
 * <h2>职责</h2>
 * 与 {@link RouteDatabaseByPreset} 类似，但不仅覆盖库，也覆盖表名。
 * 适合分片值与物理表名之间存在业务对应关系（如各租户/各渠道 -> 各自独立表）的场景。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>key：routeKey 对应的实际取值（如渠道号 "wx"/"alipay"）</li>
 *   <li>value：完整路由三元组，格式 {@code "clusterId.database.table"}，如 {@code "1.shop_db.order_wx"}</li>
 * </ul>
 * 多组 key/value 即多组映射；未命中的 value 在 {@link #calculate} 中抛 RouteException。
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * routeParamMap:
 *   "wx"    -> "1.shop_db.order_wx"
 *   "alipay"-> "2.shop_db.order_alipay"
 * </pre>
 *
 * <p>注意：此类未声明 {@code @author} 注解，沿用源码现状。</p>
 */
public class RouteTableByPreset extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteTableByPreset.class );

    /**
     * routeKey 值 -> 完整 {@link DataTable}（含 DataNode 与表名）的映射表。
     * <p>由 {@link #config()} 一次性构建，发布后只读，线程安全。</p>
     */
    private Map<String, DataTable> routeMap = new HashMap<>();

    /**
     * 解析 routeParamMap 构建 {@link #routeMap} 映射。
     * 每项 value 被切分为 {@code "clusterId.database.table"} 三段，
     * 解析失败的项记 ERROR 日志后跳过。
     */
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
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "预配置分表路由"
     */
    @Override
    public String name() {
        return "预配置分表路由";
    }

    /**
     * 算法描述与参数说明。
     *
     * @return 多行文本，含 routeParamMap 的格式约定
     */
    @Override
    public String description() {
        return """
                按照预定分表规则进行分表。
                参数说明：
                key=routeKey
                value=clusterId.database.table
                """;
    }

    /**
     * 按 routeKey 的值精确命中预设的物理表，返回其副本。
     * 完全覆盖上游 routeInfo（直接替换引用，不复用上游对象）。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   上游累积路由信息，非 null（本算法忽略其内容，直接返回命中表副本）
     * @param value       routeKey 的实际取值，非 null；必须在 {@link #routeMap} 中存在
     * @return 命中预设表的 {@link DataTable#copy()} 副本
     * @throws RouteException 当 value 在预设映射表中不存在时抛出
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataTable route = routeMap.get( value );
        if (route == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        routeInfo = route.copy();
        return routeInfo;
    }

    /**
     * 返回算法配置中的全部预设物理表（新建 ArrayList，与内部 Map 解耦）。
     * 用于 MATCH_ALL 全表扫描。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfos  上游累积列表，非 null（本算法忽略，直接返回自身预设表的新列表）
     * @return 全部预设表的新 ArrayList 副本
     * @throws RouteException 本实现不抛出
     */
    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return new ArrayList<>( this.routeMap.values() );
    }

}
