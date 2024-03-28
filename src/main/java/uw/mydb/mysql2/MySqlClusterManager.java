package uw.mydb.mysql2;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.mysql.MySqlClusterService;
import uw.mydb.protocol.codec.MysqlPacketDecoder;
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

    public static ChannelPoolMap<String, FixedChannelPool> poolMap;
    private static final Bootstrap bootstrap = new Bootstrap();

    /**
     * netty连接池使用
     *
     */
    public void init() {
        poolMap = new AbstractChannelPoolMap<String, FixedChannelPool>() {

            @Override
            protected FixedChannelPool newPool(String key) {
                ChannelPoolHandler handler = new ChannelPoolHandler() {
                    /**
                     * 使用完channel需要释放才能放入连接池
                     *
                     */
                    @Override
                    public void channelReleased(Channel ch) throws Exception {
                        // 刷新管道里的数据
                        // ch.writeAndFlush(Unpooled.EMPTY_BUFFER); // flush掉所有写回的数据
                        System.out.println("channelReleased......");
                    }

                    /**
                     * 当链接创建的时候添加channel handler，只有当channel不足时会创建，但不会超过限制的最大channel数
                     *
                     */
                    @Override
                    public void channelCreated(Channel ch) throws Exception {
                    }

                    /**
                     *  获取连接池中的channel
                     *
                     */
                    @Override
                    public void channelAcquired(Channel ch) throws Exception {
                        System.out.println("channelAcquired......");
                    }
                };

                return new FixedChannelPool(bootstrap, handler, 50); //单个host连接池大小
            }
        };

    }
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
