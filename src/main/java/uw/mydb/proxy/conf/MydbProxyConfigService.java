package uw.mydb.proxy.conf;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import uw.cache.CacheDataLoader;
import uw.cache.FusionCache;
import uw.mydb.common.conf.*;
import uw.mydb.common.report.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * mydb Config配置管理器。
 * 提供static接口，方便读取配置项。
 *
 * @author axeon
 */
public class MydbProxyConfigService {

    private static final Logger logger = LoggerFactory.getLogger( MydbProxyConfigService.class );


    /**
     * Task配置文件
     */
    private static MydbProxyProperties mydbProperties;

    /**
     * Rest模板类
     */
    private static RestTemplate authRestTemplate;

    /**
     * 基础集群ID。
     */
    private static long baseClusterId;

    /**
     * 代理ID.
     */
    private static long proxyId;


    protected MydbProxyConfigService(MydbProxyProperties mydbProperties, RestTemplate authRestTemplate) {
        MydbProxyConfigService.mydbProperties = mydbProperties;
        MydbProxyConfigService.authRestTemplate = authRestTemplate;
        //Proxy配置缓存 key: configKey value:ProxyConfig
        FusionCache.config( new FusionCache.Config( MydbProxyConfig.class, 20, 0L ), new CacheDataLoader<String, MydbProxyConfig>() {
            @Override
            public MydbProxyConfig load(String key) throws Exception {
                return authRestTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getProxyConfig?configKey=" + mydbProperties.getConfigKey(),
                        MydbProxyConfig.class );
            }
        } );

        //table配置缓存 key: configKey:tableName value:TableConfig
        FusionCache.config( new FusionCache.Config( TableConfig.class, 10000, 0L ), new CacheDataLoader<String, TableConfig>() {
            @Override
            public TableConfig load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf( ':' );
                    if (splitPos > -1) {
                        String configKey = key.substring( 0, splitPos );
                        String tableName = key.substring( splitPos + 1 );
                        TableConfig tableConfig =
                                authRestTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableConfig?configKey=" + mydbProperties.getConfigKey() +
                                        "&tableName=" + tableName, TableConfig.class );
                        return tableConfig;
                    }
                }
                return null;
            }
        } );


        //route配置缓存 key: configKey:routeId value:RouteConfig
        FusionCache.config( new FusionCache.Config( RouteConfig.class, 100, 0L ), new CacheDataLoader<String, RouteConfig>() {
            @Override
            public RouteConfig load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf( ':' );
                    if (splitPos > -1) {
                        String configKey = key.substring( 0, splitPos );
                        long routeId = Long.parseLong( key.substring( splitPos + 1 ) );
                        return authRestTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getRouteConfig?configKey=" + mydbProperties.getConfigKey() + "&routeId"
                                + "=" + routeId, RouteConfig.class );
                    }
                }
                return null;
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载route配置。
        } );


        //mysql集群配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), new CacheDataLoader<Long, MysqlClusterConfig>() {
            @Override
            public MysqlClusterConfig load(Long clusterId) throws Exception {
                MysqlClusterConfig clusterConfig =
                        authRestTemplate.getForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getMysqlCluster?configKey=" + mydbProperties.getConfigKey() + "&clusterId=" + clusterId, MysqlClusterConfig.class );
                clusterConfig.initServerWeightList();
                return clusterConfig;
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载mysqlCluster信息。
        } );

        //table列表缓存 key: clusterId:database value: HashSet<tableName>
        FusionCache.config( new FusionCache.Config( DataTable.class, 10000, 0L ), new CacheDataLoader<String, HashSet<String>>() {
            @Override
            public HashSet<String> load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf( ':' );
                    if (splitPos > -1) {
                        long clusterId = Long.parseLong( key.substring( 0, splitPos ) );
                        String database = key.substring( splitPos + 1 );
                        return authRestTemplate.exchange( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableList?clusterId=" + clusterId + "&database=" + database,
                                HttpMethod.GET, null, new org.springframework.core.ParameterizedTypeReference<HashSet<String>>() {
                        } ).getBody();
                    }
                }
                return null;
            }
        }, (key, oldValue, newValue) -> {

        } );
        //saas node缓存： key:configKey:saasId value: DataNode
        FusionCache.config( new FusionCache.Config( DataNode.class, 10000, 0L ), new CacheDataLoader<String, DataNode>() {
            @Override
            public DataNode load(String key) throws Exception {
                if (key != null) {
                    int splitPos = key.indexOf( ':' );
                    if (splitPos > -1) {
                        String configKey = key.substring( 0, splitPos );
                        String saasId = key.substring( splitPos + 1 );
                        return authRestTemplate.exchange( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getSaasNode?configKey=" + mydbProperties.getConfigKey() + "&saasId=" + saasId, HttpMethod.GET, null, DataNode.class ).getBody();
                    }
                }
                return null;
            }
        } );
    }

    /**
     * 检查是否存在指定DataTable。
     */
    public static boolean checkTableExists(String tableConfigName, DataTable dataTable) {
        HashSet<String> tableSet = FusionCache.get( DataTable.class, dataTable.getClusterId() + ":" + dataTable.getDatabase() );
        if (!tableSet.contains( dataTable.getTable() )) {
            if (logger.isDebugEnabled()) logger.debug( "向center服务器请求[{}]checkTableExists!", dataTable );
            String tableName =
                    authRestTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/checkAndCreateTable?configKey=" + mydbProperties.getConfigKey() + "&tableConfigName=" + tableConfigName + "&clusterId=" + dataTable.getClusterId() + "&database=" + dataTable.getDatabase() + "&table=" + dataTable.getTable(), null, String.class );
            if (StringUtils.isNotBlank( tableName )) {
                tableSet.add( tableName );
            }
        }
        return true;
    }


    /**
     * 检查是否存在指定DataTable。
     */
    public static List<DataTable> getTableListByPrefix(String tablePrefix) {
        //创建成功则加入set。
        ArrayList<DataTable> dataTableList =
                authRestTemplate.exchange( mydbProperties.getMydbCenterHost() + "/rpc/proxy/getTableListByPrefix?configKey=" + mydbProperties.getConfigKey() + "&tablePrefix=" + tablePrefix, HttpMethod.GET, null, new org.springframework.core.ParameterizedTypeReference<ArrayList<DataTable>>() {
        } ).getBody();
        return dataTableList;
    }

    /**
     * 获取mydb配置文件。
     *
     * @return
     */
    public static MydbProxyProperties getMydbProperties() {
        return mydbProperties;
    }

    /**
     * 获取proxy配置。
     *
     * @return
     */
    public static MydbProxyConfig getProxyConfig() {
        return FusionCache.get( MydbProxyConfig.class, mydbProperties.getConfigKey() );
    }

    /**
     * 获取表配置。
     *
     * @return
     */
    public static TableConfig getTableConfig(String tableName) {
        return FusionCache.get( TableConfig.class, mydbProperties.getConfigKey() + ":" + tableName );
    }

    /**
     * 获取路由配置。
     *
     * @return
     */
    public static RouteConfig getRouteConfig(long routeId) {
        return FusionCache.get( RouteConfig.class, mydbProperties.getConfigKey() + ":" + routeId );
    }

    /**
     * 获取SAAS节点。
     *
     * @return
     */
    public static DataNode getSaasNode(String saasId) {
        return FusionCache.get( DataNode.class, mydbProperties.getConfigKey() + ":" + saasId );
    }

    /**
     * 获取Mysql集群配置。
     *
     * @return
     */
    public static MysqlClusterConfig getMysqlCluster(long clusterId) {
        return FusionCache.get( MysqlClusterConfig.class, clusterId );
    }

    /**
     * 获取基础集群ID.
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
     * 获取表配置。
     *
     * @return
     */
    public static void putTableConfigToLocalCache(String tableName, TableConfig tableConfig) {
        FusionCache.put( TableConfig.class, mydbProperties.getConfigKey() + ":" + tableName, tableConfig, true );
    }

    /**
     * 获取proxyId
     *
     * @return
     */
    public static long getProxyId() {
        return proxyId;
    }

    /**
     * 报告proxy情况。
     *
     * @param proxyRunStats
     */
    public static void reportProxyRunStats(ProxyRunStats proxyRunStats) {
        ProxyReportResponse response = authRestTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportProxyRunStats", proxyRunStats,
                ProxyReportResponse.class );
        if (response != null) {
            proxyId = response.getProxyId();
        }
    }

    /**
     * 报告schema统计信息。
     *
     * @param schemaRunStats
     */
    public static void reportSchemaRunStats(Collection<SchemaRunStats> schemaRunStats) {
        authRestTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportSchemaRunStats", schemaRunStats, Void.class );
    }

    /**
     * 报告慢sql。
     *
     * @param slowSql
     */
    public static void reportSlowSql(SlowSql slowSql) {
        slowSql.setProxyId( getProxyId() );
        authRestTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportSlowSql", slowSql, Void.class );
    }

    /**
     * 报告错误sql。
     *
     * @param errorSql
     */
    public static void reportErrorSql(ErrorSql errorSql) {
        errorSql.setProxyId( getProxyId() );
        authRestTemplate.postForObject( mydbProperties.getMydbCenterHost() + "/rpc/proxy/reportErrorSql", errorSql, ProxyReportResponse.class );
    }


}
