package uw.mydb.proxy.route.algorithm;

import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;

import java.util.Map;

/**
 * 自动 KEY 分表路由：把任意 key 字符串作为表名后缀追加，适合无固定分布规则、按业务标识切分的场景。
 *
 * <h2>职责</h2>
 * 修改 {@link DataTable} 的 {@code table}（追加后缀）与必要时设置 {@code dataNode}。
 * 表名规则：{@code "原始表名_" + key}。配合管理端的动态建表机制，可按 key 即时拉起一张新表。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>{@code baseNode}：默认基础 DataNode，格式 {@code "clusterId.database"}。
 *       当 routeInfo 进入时尚未携带 dataNode（如算法链前段未设置库），则回落到此 baseNode。</li>
 * </ul>
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * // 表 log 按 userId 切分
 * routeParamMap: baseNode=1.log_db
 * algorithm.calculate(tableConfig, routeInfo, "u1001") -> table = "log_u1001"
 * </pre>
 *
 * @author axeon
 */
public class RouteTableByAutoKey extends RouteAlgorithm {

    /**
     * 基础数据节点，来自 routeParamMap.baseNode。
     * 当上游 routeInfo 未设置 dataNode 时作为兜底库，{@link DataTable#checkDataNode()} 返回 false 即生效。
     */
    private DataNode baseNode;

    /**
     * 解析 routeParamMap 中的 {@code baseNode} 构造 {@link DataNode}。
     */
    @Override
    public void config() {
        Map<String, String> params = routeConfig.getRouteParamMap();
        baseNode = new DataNode( params.get( "baseNode" ) );
    }


    /**
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "自动KEY分表路由"
     */
    @Override
    public String name() {
        return "自动KEY分表路由";
    }

    /**
     * 算法描述与参数说明。
     *
     * @return 多行文本，含 baseNode 格式
     */
    @Override
    public String description() {
        return """
                根据给定的key，来判断是否存在表，如果没有表，则动态自动创建以key为后缀的表。
                参数说明:
                baseNode: clusterId.database 默认基础节点，可以不指定。
                """;
    }

    /**
     * 把 value 直接拼到表名后缀；若 routeInfo 尚无 dataNode 则使用 baseNode 兜底。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   待追加后缀的路由信息，非 null
     * @param value       任意 key 字符串，非 null；将作为表名一部分，需确保不含 SQL 危险字符
     * @return 入参 routeInfo（表名已追加后缀，必要时已设置 dataNode）
     * @throws RouteException 本实现不主动抛出
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        String table = routeInfo.getTable() + "_" + value;
        routeInfo.setTable( table );
        if (!routeInfo.checkDataNode()){
            routeInfo.setDataNode( baseNode );
        }
        return routeInfo;
    }


}
