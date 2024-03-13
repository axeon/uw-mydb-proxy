package uw.mydb.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.cache.FusionCache;
import uw.httpclient.http.HttpConfig;
import uw.httpclient.http.HttpInterface;
import uw.httpclient.json.JsonInterfaceHelper;
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

    private static final HttpInterface agentClient =
            new JsonInterfaceHelper( HttpConfig.builder().connectTimeout( 30000 ).readTimeout( 30000 ).writeTimeout( 30000 ).retryOnConnectionFailure( true ).build() );


    static {
        //route配置缓存 key: routeId value:RouteConfig
        FusionCache.config( new FusionCache.Config( RouteConfig.class, 100, 0L ), (key, oldValue, newValue) -> {

        } );
        //table配置缓存 key: tableName value:TableConfig
        FusionCache.config( new FusionCache.Config( TableConfig.class, 10000, 0L ), (key, oldValue, newValue) -> {

        } );
        //mysql配置缓存 key: mysql clusterId value: mysqlClusterConfig
        FusionCache.config( new FusionCache.Config( MysqlClusterConfig.class, 10000, 0L ), (key, oldValue, newValue) -> {

        } );
        //schema meta缓存 key: clusterId.database value: List<tableName>
        FusionCache.config( new FusionCache.Config( DataTable.class, 10000, 0L ), (key, oldValue, newValue) -> {

        } );
        //saas node缓存 key:saasId value: DataNode
        FusionCache.config( new FusionCache.Config( DataNode.class, 10000, 0L ), (key, oldValue, newValue) -> {

        } );
    }

    /**
     * 检查是否存在指定DataTable。
     */
    public static boolean checkTableExists(DataTable dataTable) {
        Set<String> tableSet = FusionCache.get( DataTable.class, dataTable.getClusterId() + "." + dataTable.getDatabase() );
        return tableSet.contains( dataTable.getTable() );
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
     * 拉取完整配置。
     */
    private static MydbFullConfig loadConfig() throws Exception {
        return agentClient.getForEntity( "/agent/mydb/getConfig", MydbFullConfig.class ).getValue();
    }

}
