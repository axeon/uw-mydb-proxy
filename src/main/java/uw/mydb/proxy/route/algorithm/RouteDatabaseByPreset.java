package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预配置分库路由：根据 routeKey 的值精确映射到指定的 (clusterId, database)。
 *
 * <h2>职责</h2>
 * 仅修改 {@link DataTable} 的 {@code dataNode}（即库），不改表名。常作为算法链的"最后一段"
 * 对库做强制覆盖，使前面按 hash/mod/日期算出的库被本算法的预设值取代。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>key：routeKey 对应的实际取值（如租户号 "1001"）</li>
 *   <li>value：DataNode 字符串，格式 {@code "clusterId.database"}，如 {@code "1.shop_db"}</li>
 * </ul>
 * 多个 key/value 项即多组映射，未命中的 key 会在 {@link #calculate} 中抛 RouteException。
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * routeParamMap:
 *   "1001" -> "1.shop_db"
 *   "1002" -> "2.shop_db"
 * </pre>
 *
 * @author axeon
 */
public class RouteDatabaseByPreset extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteDatabaseByPreset.class );

    /**
     * routeKey 值 -> DataNode 的映射表。
     * <p>由 {@link #config()} 一次性构建，发布后只读，线程安全。
     * key/value 均来自 routeParamMap，单位为"分片键字符串 -> (clusterId.database)"。</p>
     */
    private Map<String, DataNode> params = new HashMap<>();

    /**
     * 解析 routeParamMap 构建 {@link #params} 映射。
     * 每项 value 被解析为 {@link DataNode}（格式 {@code "clusterId.database"}）。
     */
    @Override
    public void config() {
        for (Map.Entry<String, String> kv : routeConfig.getRouteParamMap().entrySet()) {
            params.put( kv.getKey(), new DataNode( kv.getValue()) );
        }
    }

    /**
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "预配置分库路由"
     */
    @Override
    public String name() {
        return "预配置分库路由";
    }

    /**
     * 算法描述与配置说明。
     *
     * @return 多行文本，含 routeParamMap 的格式约定
     */
    @Override
    public String description() {
        return """
                根据预设信息设置路由，此算法一般建立放在算法的最后，它会覆盖之前的配置。
                参数说明：
                key=routeKey
                value=clusterId.database
                """;
    }


    /**
     * 按 routeKey 的值精确命中预设的 DataNode，覆盖 routeInfo 的库。
     * 不修改 routeInfo.table（分表由链上其它算法负责）。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   待覆盖库的路由信息，非 null
     * @param value       routeKey 的实际取值，非 null；必须在 {@link #params} 中存在
     * @return 入参 routeInfo（已被设置 DataNode）
     * @throws RouteException 当 value 在预设映射表中不存在时抛出
     */
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
     * 枚举全部预设 DataNode，叠加到上游 routeInfos 中。
     * 用于 MATCH_ALL 全表扫描：把每个预设库都生成一份路由信息。
     * 表名取自 {@link TableConfig#getTableName()}，库取自 params。
     *
     * @param tableConfig 逻辑表配置，用于取 tableName，非 null
     * @param routeInfos  上游累积路由列表，非 null；本方法向其追加新条目
     * @return 调用 super 完成后的最终列表
     * @throws RouteException 由父类抛出
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
