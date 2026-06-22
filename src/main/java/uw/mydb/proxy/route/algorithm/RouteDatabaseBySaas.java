package uw.mydb.proxy.route.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.route.RouteAlgorithm;

/**
 * SaaS 分库路由：按租户/商户等 saasId 动态映射到对应的数据节点。
 *
 * <h2>职责</h2>
 * 仅修改 {@link DataTable} 的 {@code dataNode}（即库），不改表名。
 * 与 {@link RouteDatabaseByPreset} 不同的是，映射表不写在 routeParamMap 里，
 * 而是运行时通过 {@link MydbProxyConfigService#getSaasNode(String)} 实时查询管理端配置，
 * 适合 saasId 数量多、动态变化的场景。
 *
 * <h2>配置参数（routeParamMap）</h2>
 * 本算法不读取 routeParamMap，{@link #config()} 为空实现。映射关系完全由管理端维护。
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * // saasId=8001 的租户路由到管理端配置的 DataNode
 * DataTable hit = algorithm.calculate(tableConfig, defaultRoute, "8001");
 * </pre>
 *
 * @author axeon
 */
public class RouteDatabaseBySaas extends RouteAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger( RouteDatabaseBySaas.class );

    /**
     * 空实现：本算法不解析 routeParamMap，saasId -> DataNode 的映射由管理端 API 动态维护。
     */
    @Override
    public void config() {

    }

    /**
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "saas分库路由"
     */
    @Override
    public String name() {
        return "saas分库路由";
    }

    /**
     * 算法描述。
     *
     * @return 多行文本，说明映射来自管理端 API
     */
    @Override
    public String description() {
        return """
                特别为saas模式优化的一种分库方案。
                执行的时候调用管理端的API。
                """;
    }

    /**
     * 根据 saasId 查询管理端映射，覆盖 routeInfo 的库；不改表名。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   待覆盖库的路由信息，非 null
     * @param value       saasId 字符串，非 null；需要在管理端存在对应映射
     * @return 入参 routeInfo（已被设置 DataNode）
     * @throws RouteException 当管理端未找到 saasId 对应的 DataNode 时抛出
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        DataNode saasNode = MydbProxyConfigService.getSaasNode( value );
        if (saasNode == null) {
            throw new RouteException( "calculate计算失败，参数值[" + value + "]无法找到对应路由！" );
        }
        routeInfo.setDataNode( saasNode );
        return routeInfo;
    }

}
