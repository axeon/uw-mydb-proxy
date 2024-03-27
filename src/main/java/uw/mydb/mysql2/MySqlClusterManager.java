package uw.mydb.mysql2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
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

    /**
     * 根据mysqlClusterId获得对应的mysqlGroupService。
     *
     * @param clusterId
     * @return
     */
    public static MySqlClusterService getMysqlClusterService(Long clusterId) {
        return mysqlClusterServiceMap.computeIfAbsent( clusterId, key -> {
            MysqlClusterConfig clusterConfig = MydbConfigService.getMysqlCluster( clusterId );
            if (clusterConfig == null) {
                return null;
            }
            MySqlClusterService clusterService = new MySqlClusterService( clusterConfig );
            return clusterService;
        } );
    }

    public static Map<Long, MySqlClusterService> getMysqlClusterServiceMap() {
        return mysqlClusterServiceMap;
    }

    /**
     * 启动mysql集群检查线程。
     */
    public static boolean start() {
        if (STATE.compareAndSet( false, true )) {
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
            return true;
        } else {
            return false;
        }
    }


}
