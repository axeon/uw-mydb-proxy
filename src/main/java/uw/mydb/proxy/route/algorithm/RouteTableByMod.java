package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * 取模分表路由：把 long 值取模到预设表数量上，简单均匀分布。
 *
 * <h2>职责</h2>
 * 把 routeKey 解析为 long 后对预设表数取模，命中一张物理表。分布均匀、计算开销极低，
 * 但扩容（增加表）会导致大面积数据迁移，适合表数固定的场景。不支持 RANGE/MULTI 批量优化。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>{@code routeList}：逗号分隔的物理表清单，每项格式 {@code "clusterId.database.table"}。
 *       表数即模数。未配置或全部解析失败会导致 {@link #calculate} 抛 NPE 或除零异常，
 *       需保证配置正确。</li>
 * </ul>
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * routeParamMap: routeList=1.shop_db.order_0,1.shop_db.order_1,...,1.shop_db.order_15
 * // userId=8001 -> 8001 % 16 = 1 -> order_1
 * algorithm.calculate(tableConfig, routeInfo, "8001") -> order_1 的副本
 * </pre>
 *
 * <h2>负数与 Long.MIN_VALUE</h2>
 * 使用 {@link Math#floorMod(long, long)} 而非 {@code value % n}，因为：
 * <ul>
 *   <li>Java 的 {@code %} 对负数返回负数，直接当下标会 {@link IndexOutOfBoundsException}。</li>
 *   <li>不能改用 {@code Math.abs(value) % n}：{@code Math.abs(Long.MIN_VALUE)} 仍是负数
 *       （溢出回绕），仍会越界。floorMod 保证结果始终落在 {@code [0, n)}。</li>
 * </ul>
 *
 * @author axeon
 */
public class RouteTableByMod extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(RouteTableByMod.class);


    /**
     * 预设的物理表清单，从 {@code routeList} 解析得到，表数即取模的模数。
     * <p>由 {@link #config()} 构造，发布后只读。空列表表示配置错误，
     * 调用 {@link #calculate} 会因除零抛 ArithmeticException，调用方需保证配置可用。</p>
     * 初始为空 ArrayList。
     */
    private List<DataTable> routeInfos = new ArrayList<>();

    /**
     * 解析 routeParamMap.routeList，构造 {@link #routeInfos}。
     * <p>每项格式 {@code "clusterId.database.table"}，解析失败的项记 ERROR 日志后跳过。
     * routeList 未配置时仅记日志，routeInfos 保持空。</p>
     */
    @Override
    public void config() {
        String routeList = this.routeConfig.getRouteParamMap().get("routeList");
        if (routeList == null || routeList.isBlank()) {
            logger.error("RouteTableByMod参数配置错误！routeList未配置，routeId=[{}]", this.routeConfig.getId());
            return;
        }
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
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "Mod分表路由"
     */
    @Override
    public String name() {
        return "Mod分表路由";
    }

    /**
     * 算法描述与参数说明。
     *
     * @return 多行文本，含 routeList 格式
     */
    @Override
    public String description() {
        return """
                根据给定的long值，按照表数量直接mod分表。
                参数说明:
                routeList: clusterId.database.table,clusterId.database.table,...
                """;
    }

    /**
     * 把字符串 value 解析为 long，对表数取模命中一张预设表，返回其副本。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   上游累积路由信息，非 null（本算法不基于此计算，直接返回命中表副本）
     * @param value       数值字符串，必须可被 {@link Long#parseLong} 解析；非 null
     * @return 命中预设表的 {@link DataTable#copy()} 副本
     * @throws RouteException 当 value 不是合法 long 时抛出
     * @throws ArithmeticException 当 {@link #routeInfos} 为空（取模分母为 0）时抛出
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        long longValue = -1L;

        try {
            longValue = Long.parseLong(value);
        } catch (Exception e) {
            throw new RouteException("calculate计算失败，参数值[" + value + "]错误！");
        }

        //使用Math.floorMod避免Math.abs(Long.MIN_VALUE)溢出导致的负数下标越界
        int index = Math.floorMod(longValue, routeInfos.size());
        routeInfo = routeInfos.get(index).copy();
        return routeInfo;
    }


    /**
     * 返回算法配置中的全部预设物理表。
     * 用于 MATCH_ALL 全表扫描。直接返回内部引用 {@link #routeInfos}，调用方不应修改。
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
