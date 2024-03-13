package uw.mydb.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.vo.MydbFullConfig;
import uw.mydb.vo.MydbMainConfig;
import uw.mydb.vo.MysqlClusterConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * mysql组服务的管理器。
 *
 * @author axeon
 */
public class MySqlClusterManager {

    private static final Logger logger = LoggerFactory.getLogger( MySqlClusterManager.class );

    /**
     * 当前启动状态.
     */
    private static final AtomicBoolean STATE = new AtomicBoolean( false );

    /**
     * mysql集群列表。
     */
    private static Map<Long, MySqlClusterService> mysqlClusterServiceMap = new HashMap<>();

    private static MydbFullConfig config;

    /**
     * 根据mysqlGroupName获得对应的mysqlGroupService。
     *
     * @param clusterId
     * @return
     */
    public static MySqlClusterService getMysqlClusterService(Long clusterId) {
        return mysqlClusterServiceMap.get( clusterId );
    }

    public static Map<Long, MySqlClusterService> getMysqlClusterServiceMap() {
        return mysqlClusterServiceMap;
    }

    /**
     * 初始化。
     */
    public static void init() {
        for (Map.Entry<Long, MysqlClusterConfig> kv : config.getMysqlClusterMap().entrySet()) {
            MySqlClusterService clusterService = new MySqlClusterService( kv.getValue() );
            clusterService.init();
            mysqlClusterServiceMap.put( kv.getKey(), clusterService );
        }
    }

    /**
     * 启动mysql集群检查线程。
     */
    public static boolean start() {
        if (STATE.compareAndSet( false, true )) {
            MySqlMaintenanceService.start();
            for (MySqlClusterService clusterService : mysqlClusterServiceMap.values()) {
                clusterService.start();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 停止mysql集群。
     */
    public static boolean stop() {
        if (STATE.compareAndSet( true, false )) {
            for (MySqlClusterService clusterService : mysqlClusterServiceMap.values()) {
                clusterService.stop();
            }
            MySqlMaintenanceService.stop();
            return true;
        } else {
            return false;
        }
    }


}
