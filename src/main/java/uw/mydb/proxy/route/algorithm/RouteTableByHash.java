package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.proxy.util.ConsistentHash;

import java.util.ArrayList;
import java.util.List;

/**
 * 一致性 Hash 分表路由：按 KEY 的 hash 值在预设表集合中分配，扩缩容时迁移量最小。
 *
 * <h2>职责</h2>
 * 把 routeKey 的字符串值经一致性 hash 路由到预设的物理表之一。底层依赖 {@link ConsistentHash}，
 * 每个真实节点有 128 个虚拟节点，分布更均匀。不支持 RANGE。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>{@code routeList}：逗号分隔的物理表清单，每项格式 {@code "clusterId.database.table"}。
 *       至少 1 项；未配置或全部解析失败时算法会进入空状态，{@link #calculate} 会因
 *       consistentHash 为 null 抛 NPE，需保证配置正确。</li>
 * </ul>
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * routeParamMap: routeList=1.shop_db.order_0,1.shop_db.order_1,2.shop_db.order_2
 * algorithm.calculate(tableConfig, routeInfo, "user_8001") -> 某张预设表的副本
 * </pre>
 *
 * @author axeon
 */
public class RouteTableByHash extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteTableByHash.class );

    /**
     * 一致性 Hash 算法对象，key 为字符串，value 为 {@link DataTable}。
     * <p>由 {@link #config()} 构造，虚拟节点数 128。构造完成后只读，线程安全。</p>
     * null 表示尚未调用 config() 或配置错误。
     */
    private ConsistentHash<DataTable> consistentHash = null;

    /**
     * 预设的物理表清单，从 {@code routeList} 解析得到。
     * <p>由 {@link #config()} 构造，发布后只读。供 {@link #getAllRouteList} 枚举使用。</p>
     * 初始为空 ArrayList。
     */
    private List<DataTable> routeInfos = new ArrayList<>();

    /**
     * 解析 routeParamMap.routeList，构造 {@link #routeInfos} 与 {@link #consistentHash}。
     * <p>每项格式 {@code "clusterId.database.table"}，解析失败的项记 ERROR 日志后跳过。
     * 虚拟节点数固定 128。routeList 未配置时仅记日志，consistentHash 保持 null。</p>
     */
    @Override
    public void config() {
        String routeList = this.routeConfig.getRouteParamMap().get( "routeList" );
        if (routeList == null || routeList.isBlank()) {
            logger.error( "RouteTableByHash参数配置错误！routeList未配置，routeId=[{}]", this.routeConfig.getId() );
            return;
        }
        for (String route : routeList.split( "," )) {
            String[] data = route.split( "\\." );
            if (data.length != 3) {
                logger.error( "参数配置错误！route:[{}]", route );
                continue;
            }
            routeInfos.add(new DataTable( new DataNode(Long.parseLong( data[0] ), data[1]), data[2] ) );
        }
        consistentHash = new ConsistentHash<>( 128, routeInfos );
    }

    /**
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "Hash分表路由"
     */
    @Override
    public String name() {
        return "Hash分表路由";
    }

    /**
     * 算法描述与参数说明。
     *
     * @return 多行文本，含 routeList 格式
     */
    @Override
    public String description() {
        return """
                根据给定的KEY值，按照表数量hash分表。
                参数说明:
                routeList: clusterId.database.table,clusterId.database.table,...
                """;
    }

    /**
     * 按一致性 hash 选出一张预设表，返回其副本（避免多请求共享同一可变实例）。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   上游累积路由信息，非 null（本算法不基于此计算，仅按 value 选表）
     * @param value       routeKey 字符串值，非 null；将作为 hash 输入
     * @return 命中预设表的 {@link DataTable#copy()} 副本
     * @throws RouteException 当 consistentHash 未初始化导致 {@link ConsistentHash#get} 返回 null 时抛出
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataTable route = consistentHash.get( value );
        if (route == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]错误！" );
        }
        return route.copy();
    }

    /**
     * 返回算法配置中的全部预设物理表。
     * 用于 MATCH_ALL 全表扫描。注意直接返回内部引用 {@link #routeInfos}，调用方不应修改。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfos  上游累积列表，非 null（本算法忽略，直接返回自身预设表）
     * @return {@link #routeInfos}
     * @throws RouteException 本实现不抛出
     */
    @Override
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return this.routeInfos;
    }

}
