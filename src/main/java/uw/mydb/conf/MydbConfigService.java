package uw.mydb.conf;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.cache.CacheDataLoader;
import uw.cache.FusionCache;
import uw.httpclient.http.HttpConfig;
import uw.httpclient.http.HttpInterface;
import uw.httpclient.json.JsonInterfaceHelper;
import uw.mydb.vo.*;

import java.util.List;
import java.util.Set;

/**
 * mydb Config配置管理器。
 * 提供static接口，方便读取配置项。
 *
 * @author axeon
 */
public class MydbConfigService {

    private static final Logger logger = LoggerFactory.getLogger( MydbConfigService.class );

    private static final HttpInterface agentClient =
            new JsonInterfaceHelper( HttpConfig.builder().connectTimeout( 30000 ).readTimeout( 30000 ).writeTimeout( 30000 ).retryOnConnectionFailure( true ).build() );

    private static String mydbCenter;

    private static long configId;

    static {
        //Proxy配置缓存 key: routeId value:ProxyConfig
        FusionCache.config( new FusionCache.Config( MydbProxyConfig.class, 1, 0L ), new CacheDataLoader<Long, MydbProxyConfig>() {
            @Override
            public MydbProxyConfig load(Long key) throws Exception {
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getProxyConfig?configId=" + configId, MydbProxyConfig.class ).getValue();
            }
        } );

        //table配置缓存 key: tableName value:TableConfig
        FusionCache.config( new FusionCache.Config( TableConfig.class, 10000, 0L ), new CacheDataLoader<String, TableConfig>() {
            @Override
            public TableConfig load(String key) throws Exception {
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getTableConfig?configId=" + configId + "&tableName=" + key, TableConfig.class ).getValue();
            }
        });

        //route配置缓存 key: routeId value:RouteConfig
        FusionCache.config( new FusionCache.Config( RouteConfig.class, 100, 0L ), new CacheDataLoader<Long, RouteConfig>() {
            @Override
            public RouteConfig load(Long key) throws Exception {
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getRouteConfig?configId=" + configId + "&routeId=" + key, RouteConfig.class ).getValue();
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载route配置。
        } );

        //mysql配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), new CacheDataLoader<Long, MysqlClusterConfig>() {
            @Override
            public MysqlClusterConfig load(Long key) throws Exception {
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getMysqlCluster?configId=" + configId + "&clusterId=" + key, MysqlClusterConfig.class ).getValue();
            }
        }, (key, oldValue, newValue) -> {
            //此处要重新加载mysqlCluster信息。
        } );

        //schema meta缓存 key: clusterId.database value: Set<tableName>
        FusionCache.config( new FusionCache.Config( DataTable.class, 10000, 0L ), new CacheDataLoader<String, Set<String>>() {
            @Override
            public Set<String> load(String key) throws Exception {
                DataNode node = new DataNode( key );
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getTableList?clusterId=" + node.getClusterId() + "&database=" + node.getDatabase(),
                        new TypeReference<Set<String>>() {
                } ).getValue();
            }
        }, (key, oldValue, newValue) -> {

        } );

        //saas node缓存 key:saasId value: DataNode
        FusionCache.config( new FusionCache.Config( DataNode.class, 10000, 0L ), new CacheDataLoader<Long, List<DataNode>>() {
            @Override
            public List<DataNode> load(Long key) throws Exception {
                return agentClient.getForEntity( mydbCenter + "/agent/mydb/getSaasNode?configId=" + configId + "&tableName=" + key, new TypeReference<List<DataNode>>() {
                } ).getValue();
            }
        });
    }

    /**
     * 检查是否存在指定DataTable。
     */
    public static boolean checkTableExists(DataTable dataTable) {
        Set<String> tableSet = FusionCache.get( DataTable.class, dataTable.getClusterId() + "." + dataTable.getDatabase() );
        if (!tableSet.contains( dataTable.getTable() )) {
            //通知服务器创建表。
            //创建成功则加入set。
        }
        return true;
    }

    /**
     * 获得proxy配置。
     *
     * @param configId
     * @return
     */
    public static MydbProxyConfig getProxyConfig(String configId) {
        return FusionCache.get( MydbProxyConfig.class, configId );
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
     * 设置初始化参数。
     *
     * @param mydbCenter
     * @param configId
     */
    protected static final void initProperties(String mydbCenter, long configId) {
        MydbConfigService.mydbCenter = mydbCenter;
        MydbConfigService.configId = configId;
    }


}
