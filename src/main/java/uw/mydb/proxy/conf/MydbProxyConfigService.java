package uw.mydb.proxy.conf;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import uw.cache.CacheDataLoader;
import uw.cache.FusionCache;
import uw.mydb.common.conf.*;
import uw.mydb.common.report.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * mydb 配置管理器，全静态接口。
 * <p>
 * 在构造器（由 {@link MydbProxySpringAutoConfiguration} 注入）中注册多个 {@link FusionCache}：
 * <ul>
 *   <li>{@link MydbProxyConfig}：key = {@code configKey}，容量 20。RPC 拉取 proxy 总配置（用户名/密码/baseCluster 等）。</li>
 *   <li>{@link TableConfig}：key = {@code configKey:tableName}，容量 10000。RPC 拉取表分片配置。</li>
 *   <li>{@link RouteConfig}：key = {@code configKey:routeId}，容量 100。RPC 拉取路由算法配置。</li>
 *   <li>{@link MysqlClusterConfig}：key = {@code clusterId}，容量 10000。RPC 拉取 MySQL 集群配置（含 serverList 与权重）。</li>
 *   <li>{@code HashSet<String>}（DataTable 类型 key）：key = {@code clusterId:database}，容量 10000。RPC 拉取某库的表名集合，用于 {@link #ensureTableExists} 判定。</li>
 *   <li>{@link DataNode}：key = {@code configKey:saasId}，容量 10000。RPC 拉取 SaaS 路由节点。</li>
 * </ul>
 * RPC 均走 {@link #authRestClient}（带鉴权），失败时 {@link #getForObject} 内置 3 次重试（间隔 1s）。
 * <p>
 * 提供 {@link #ensureTableExists} 动态建表：发现目标表不在本地缓存表名集合内时，POST 到 center 触发建表并加入集合。
 *
 * @author axeon
 */
public class MydbProxyConfigService {

    private static final Logger logger = LoggerFactory.getLogger(MydbProxyConfigService.class);

    /**
     * proxy 配置属性（启动时注入，运行期不变）。
     */
    private static MydbProxyProperties mydbProperties;

    /**
     * 带鉴权拦截器的 RestClient，用于向 mydb-center 发起 RPC。
     */
    private static RestClient authRestClient;

    /**
     * 基础集群 ID（懒加载自 {@link MydbProxyConfig#getBaseCluster()}）。volatile 保证多线程可见性。
     */
    private static volatile long baseClusterId;

    /**
     * 当前 proxy 实例在 center 注册后返回的 proxyId（用于上报统计归属）。
     */
    private static long proxyId;


    /**
     * 构造配置管理器并注册全部 FusionCache 数据加载器。由 Spring 自动配置注入。
     *
     * @param mydbProperties proxy 配置属性
     * @param authRestClient 带鉴权的 RestClient
     */
    protected MydbProxyConfigService(MydbProxyProperties mydbProperties, RestClient authRestClient) {
        MydbProxyConfigService.mydbProperties = mydbProperties;
        MydbProxyConfigService.authRestClient = authRestClient;
        //Proxy配置缓存 key: configKey value:ProxyConfig
        FusionCache.config(new FusionCache.Config(MydbProxyConfig.class, 20, 0L), new CacheDataLoader<String, MydbProxyConfig>() {
            @Override
            public MydbProxyConfig load(String key) throws Exception {
                return getForObject(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getProxyConfig?configKey=" + mydbProperties.getConfigKey(),
                        MydbProxyConfig.class);
            }
        });

        //table配置缓存 key: configKey:tableName value:TableConfig
        FusionCache.config(new FusionCache.Config(TableConfig.class, 10000, 0L), new CacheDataLoader<String, TableConfig>() {
            @Override
            public TableConfig load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf(':');
                    if (splitPos > -1) {
                        String tableName = key.substring(splitPos + 1);
                        return getForObject(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableConfig?configKey=" + mydbProperties.getConfigKey() +
                                "&tableName=" + tableName, TableConfig.class);
                    }
                }
                return null;
            }
        });


        //route配置缓存 key: configKey:routeId value:RouteConfig
        FusionCache.config(new FusionCache.Config(RouteConfig.class, 100, 0L), new CacheDataLoader<String, RouteConfig>() {
            @Override
            public RouteConfig load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf(':');
                    if (splitPos > -1) {
                        long routeId = Long.parseLong(key.substring(splitPos + 1));
                        return getForObject(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getRouteConfig?configKey=" + mydbProperties.getConfigKey() + "&routeId=" + routeId, RouteConfig.class);
                    }
                }
                return null;
            }
        }, (key, oldValue, newValue) -> {
        });


        //mysql集群配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config(new FusionCache.Config(MysqlClusterConfig.class, 10000, 0L), new CacheDataLoader<Long, MysqlClusterConfig>() {
            @Override
            public MysqlClusterConfig load(Long clusterId) throws Exception {
                MysqlClusterConfig clusterConfig =
                        getForObject(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getMysqlCluster?configKey=" + mydbProperties.getConfigKey() + "&clusterId=" + clusterId, MysqlClusterConfig.class);
                clusterConfig.initServerWeightList();
                return clusterConfig;
            }
        }, (key, oldValue, newValue) -> {
        });

        //table列表缓存 key: clusterId:database value: HashSet<tableName>
        FusionCache.config(new FusionCache.Config(DataTable.class, 10000, 0L), new CacheDataLoader<String, HashSet<String>>() {
            @Override
            public HashSet<String> load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf(':');
                    if (splitPos > -1) {
                        long clusterId = Long.parseLong(key.substring(0, splitPos));
                        String database = key.substring(splitPos + 1);
                        return authRestClient.get()
                                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableList?clusterId=" + clusterId + "&database=" + database)
                                .retrieve()
                                .body(new ParameterizedTypeReference<HashSet<String>>() {});
                    }
                }
                return null;
            }
        }, (key, oldValue, newValue) -> {
        });

        //saas node缓存： key:configKey:saasId value: DataNode
        FusionCache.config(new FusionCache.Config(DataNode.class, 10000, 0L), new CacheDataLoader<String, DataNode>() {
            @Override
            public DataNode load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf(':');
                    if (splitPos > -1) {
                        String saasId = key.substring(splitPos + 1);
                        return authRestClient.get()
                                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getSaasNode?configKey=" + mydbProperties.getConfigKey() + "&saasId=" + saasId)
                                .retrieve()
                                .body(DataNode.class);
                    }
                }
                return null;
            }
        });
    }

    /**
     * RPC 失败重试次数（含首次共 3 次）。
     */
    private static final int RPC_RETRY_COUNT = 3;
    /**
     * RPC 重试间隔（毫秒）。
     */
    private static final long RPC_RETRY_INTERVAL_MS = 1000;

    /**
     * 带 3 次重试的 GET RPC。全部失败后返回 null（不抛异常，调用方需处理 null）。中断时提前返回。
     *
     * @param url          完整 RPC URL
     * @param responseType 响应类型
     * @param <T>          响应泛型
     * @return 响应对象，失败返回 null
     */
    private static <T> T getForObject(String url, Class<T> responseType) {
        Exception lastEx = null;
        for (int i = 0; i < RPC_RETRY_COUNT; i++) {
            try {
                return authRestClient.get()
                        .uri(url)
                        .retrieve()
                        .body(responseType);
            } catch (Exception e) {
                lastEx = e;
                logger.warn("RPC请求失败[第{}次]: {}", i + 1, e.getMessage());
                if (i < RPC_RETRY_COUNT - 1) {
                    try {
                        Thread.sleep(RPC_RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        logger.error("RPC请求最终失败: {}", lastEx == null ? "unknown" : lastEx.getMessage(), lastEx);
        return null;
    }

    /**
     * 确保指定 {@link DataTable} 在目标库中存在，不存在则向 center 请求建表（动态分表场景）。
     * <p>
     * 判定逻辑：从 FusionCache 取出 {@code clusterId:database} 的表名集合，若目标表不在其中则 POST checkAndCreateTable，
     * center 完成建表后返回实际表名（可能因并发而由其他节点已建），将其加入本地集合。
     *
     * @param tableConfigName 表配置名（用于在 center 找到建表 SQL）
     * @param dataTable       目标数据表（clusterId + database + table）
     * @return true 表示表已存在或建表成功；false 表示建表失败
     */
    public static boolean ensureTableExists(String tableConfigName, DataTable dataTable) {
        HashSet<String> tableSet = FusionCache.get(DataTable.class, dataTable.getClusterId() + ":" + dataTable.getDatabase());
        if (tableSet != null && !tableSet.contains(dataTable.getTable())) {
            if (logger.isDebugEnabled()) logger.debug("向center服务器请求[{}]ensureTableExists!", dataTable);
            String tableName =
                    authRestClient.post()
                            .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/checkAndCreateTable?configKey=" + mydbProperties.getConfigKey() + "&tableConfigName=" + tableConfigName + "&clusterId=" + dataTable.getClusterId() + "&database=" + dataTable.getDatabase() + "&table=" + dataTable.getTable())
                            .retrieve()
                            .body(String.class);
            if (StringUtils.isNotBlank(tableName)) {
                tableSet.add(tableName);
                return true;
            }
            return false;
        }
        return true;
    }


    /**
     * 根据前缀获取表列表。
     *
     * @param tablePrefix 表名前缀
     * @return 数据表列表
     */
    public static List<DataTable> getTableListByPrefix(String tablePrefix) {
        return authRestClient.get()
                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableListByPrefix?configKey=" + mydbProperties.getConfigKey() + "&tablePrefix=" + tablePrefix)
                .retrieve()
                .body(new ParameterizedTypeReference<ArrayList<DataTable>>() {});
    }

    /**
     * 获取配置属性。
     *
     * @return MydbProxy配置属性
     */
    public static MydbProxyProperties getMydbProperties() {
        return mydbProperties;
    }

    /**
     * 获取代理配置。
     *
     * @return 代理配置
     */
    public static MydbProxyConfig getProxyConfig() {
        return FusionCache.get(MydbProxyConfig.class, mydbProperties.getConfigKey());
    }

    /**
     * 获取表配置。
     *
     * @param tableName 表名
     * @return 表配置
     */
    public static TableConfig getTableConfig(String tableName) {
        return FusionCache.get(TableConfig.class, mydbProperties.getConfigKey() + ":" + tableName);
    }

    /**
     * 获取路由配置。
     *
     * @param routeId 路由ID
     * @return 路由配置
     */
    public static RouteConfig getRouteConfig(long routeId) {
        return FusionCache.get(RouteConfig.class, mydbProperties.getConfigKey() + ":" + routeId);
    }

    /**
     * 获取SaaS节点数据。
     *
     * @param saasId SaaS ID
     * @return 数据节点
     */
    public static DataNode getSaasNode(String saasId) {
        return FusionCache.get(DataNode.class, mydbProperties.getConfigKey() + ":" + saasId);
    }

    /**
     * 获取MySQL集群配置。
     *
     * @param clusterId 集群ID
     * @return MySQL集群配置
     */
    public static MysqlClusterConfig getMysqlCluster(long clusterId) {
        return FusionCache.get(MysqlClusterConfig.class, clusterId);
    }

    /**
     * 获取基础集群ID。
     *
     * @return 基础集群ID
     */
    public static long getBaseClusterId() {
        if (baseClusterId == 0) {
            MydbProxyConfig proxyConfig = getProxyConfig();
            if (proxyConfig != null) {
                baseClusterId = proxyConfig.getBaseCluster();
            }
        }
        return baseClusterId;
    }

    /**
     * 将表配置写入本地缓存。
     *
     * @param tableName 表名
     * @param tableConfig 表配置
     */
    public static void putTableConfigToLocalCache(String tableName, TableConfig tableConfig) {
        FusionCache.put(TableConfig.class, mydbProperties.getConfigKey() + ":" + tableName, tableConfig, true);
    }

    /**
     * 获取代理ID。
     *
     * @return 代理ID
     */
    public static long getProxyId() {
        return proxyId;
    }

    /**
     * 上报代理运行统计信息。
     *
     * @param proxyRunStats 代理运行统计
     */
    public static void reportProxyRunStats(ProxyRunStats proxyRunStats) {
        ProxyReportResponse response = authRestClient.post()
                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportProxyRunStats")
                .body(proxyRunStats)
                .retrieve()
                .body(ProxyReportResponse.class);
        if (response != null) {
            proxyId = response.getProxyId();
        }
    }

    /**
     * 上报Schema运行统计信息。
     *
     * @param schemaRunStats Schema运行统计集合
     */
    public static void reportSchemaRunStats(Collection<SchemaRunStats> schemaRunStats) {
        authRestClient.post()
                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportSchemaRunStats")
                .body(schemaRunStats)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 上报慢SQL。
     *
     * @param slowSql 慢SQL信息
     */
    public static void reportSlowSql(SlowSql slowSql) {
        slowSql.setProxyId(getProxyId());
        authRestClient.post()
                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportSlowSql")
                .body(slowSql)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 上报错误SQL。
     *
     * @param errorSql 错误SQL信息
     */
    public static void reportErrorSql(ErrorSql errorSql) {
        errorSql.setProxyId(getProxyId());
        authRestClient.post()
                .uri(mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportErrorSql")
                .body(errorSql)
                .retrieve()
                .body(ProxyReportResponse.class);
    }


}
