package uw.mydb.conf;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import uw.cache.CacheDataLoader;
import uw.cache.FusionCache;
import uw.mydb.stats.vo.ProxyRunReport;
import uw.mydb.vo.*;

import java.util.Set;

/**
 * mydb Config配置管理器。
 * 提供static接口，方便读取配置项。
 *
 * @author axeon
 */
public class MydbConfigService {

    private static final Logger logger = LoggerFactory.getLogger( MydbConfigService.class );

    /**
     * Task配置文件
     */
    private static MydbProperties mydbProperties;

    /**
     * Rest模板类
     */
    private static RestTemplate restTemplate;

    /**
     * 基础集群ID。
     */
    private static long baseClusterId;

    /**
     * 代理ID.
     */
    private static long proxyId;


    protected MydbConfigService(MydbProperties mydbProperties, RestTemplate restTemplate) {
        MydbConfigService.mydbProperties = mydbProperties;
        MydbConfigService.restTemplate = restTemplate;
        //Proxy配置缓存 key: routeId value:ProxyConfig
        FusionCache.config( new FusionCache.Config( MydbProxyConfig.class, 1, 0L ), new CacheDataLoader<String, MydbProxyConfig>() {
            @Override
            public MydbProxyConfig load(String key) throws Exception {
                return restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getProxyConfig?configKey=" + mydbProperties.getConfigKey(),
                        MydbProxyConfig.class );
            }
        } );

        //table配置缓存 key: tableName value:TableConfig
        FusionCache.config( new FusionCache.Config( TableConfig.class, 10000, 0L ), new CacheDataLoader<String, TableConfig>() {
            @Override
            public TableConfig load(String key) throws Exception {
                return restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getTableConfig?configKey=" + mydbProperties.getConfigKey() + "&tableName=" + key, TableConfig.class );
            }
        } );

        //route配置缓存 key: routeId value:RouteConfig
        FusionCache.config( new FusionCache.Config( RouteConfig.class, 100, 0L ), new CacheDataLoader<Long, RouteConfig>() {
            @Override
            public RouteConfig load(Long key) throws Exception {
                return restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getRouteConfig?configKey=" + mydbProperties.getConfigKey() + "&routeId=" + key
                        , RouteConfig.class );
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载route配置。
        } );

        //mysql配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), new CacheDataLoader<Long, MysqlClusterConfig>() {
            @Override
            public MysqlClusterConfig load(Long key) throws Exception {
                MysqlClusterConfig clusterConfig =
                        restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getMysqlCluster?configKey=" + mydbProperties.getConfigKey() + "&clusterId=" + key, MysqlClusterConfig.class );
                clusterConfig.initServerWeightList();
                return clusterConfig;
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载mysqlCluster信息。
        } );

        //schema meta缓存 key: clusterId.database value: tableName[]
        FusionCache.config( new FusionCache.Config( DataTable.class, 10000, 0L ), new CacheDataLoader<String, String[]>() {
            @Override
            public String[] load(String key) throws Exception {
                DataNode node = new DataNode( key );
                return restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getTableList?clusterId=" + node.getClusterId() + "&database=" + node.getDatabase(), String[].class );
            }
        }, (key, oldValue, newValue) -> {

        } );

        //saas node缓存 key:saasId value: DataNode[]
        FusionCache.config( new FusionCache.Config( DataNode.class, 10000, 0L ), new CacheDataLoader<Long, DataNode[]>() {
            @Override
            public DataNode[] load(Long key) throws Exception {
                return restTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getSaasNode?configKey=" + mydbProperties.getConfigKey() + "&tableName=" + key,
                        DataNode[].class );
            }
        } );
    }

    /**
     * 检查是否存在指定DataTable。
     */
    public static boolean checkTableExists(String tableConfigName, DataTable dataTable) {
        Set<String> tableSet = FusionCache.get( DataTable.class, dataTable.getClusterId() + "." + dataTable.getDatabase() );
        if (!tableSet.contains( dataTable.getTable() )) {
            //创建成功则加入set。
            String createdTable =
                    MydbConfigService.restTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/getSaasNode?configKey=" + mydbProperties.getConfigKey() +
                            "&tableConfigName=" + tableConfigName + "&clusterId=" + dataTable.getClusterId() + "&database=" + dataTable.getDatabase() + "&table=" + dataTable.getTable(), null, String.class );
            if (StringUtils.isNotBlank( createdTable )) {
                tableSet.add( createdTable );
            }
        }
        return true;
    }


    /**
     * 获得mydb配置文件。
     *
     * @return
     */
    public static MydbProperties getMydbProperties() {
        return mydbProperties;
    }

    /**
     * 获得proxy配置。
     *
     * @return
     */
    public static MydbProxyConfig getProxyConfig() {
        return FusionCache.get( MydbProxyConfig.class, mydbProperties.getConfigKey() );
    }

    /**
     * 获得基础集群ID.
     *
     * @return
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
     * 获得表配置。
     *
     * @return
     */
    public static TableConfig getTableConfig(String tableName) {
        return FusionCache.get( TableConfig.class, tableName );
    }

    /**
     * 获得路由配置。
     *
     * @return
     */
    public static RouteConfig getRouteConfig(long routeId) {
        return FusionCache.get( RouteConfig.class, routeId );
    }

    /**
     * 获得SAAS节点。
     *
     * @return
     */
    public static DataNode getSaasNode(String saasId) {
        return FusionCache.get( DataNode.class, saasId );
    }

    /**
     * 获得Mysql集群配置。
     *
     * @return
     */
    public static MysqlClusterConfig getMysqlCluster(long clusterId) {
        return FusionCache.get( MysqlClusterConfig.class, clusterId );
    }

    /**
     * 获得proxyId
     *
     * @return
     */
    public static long getProxyId() {
        return proxyId;
    }

    /**
     * 报告执行情况。
     *
     * @param report
     */
    public static void report(ProxyRunReport report) {
        ProxyReportResponse response = restTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/agent/report", report, ProxyReportResponse.class );
        if (response != null) {
            proxyId = response.getProxyId();
        }
    }
}
