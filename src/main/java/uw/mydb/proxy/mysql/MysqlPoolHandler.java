package uw.mydb.proxy.mysql;

import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.MysqlServerConfig;
import uw.mydb.proxy.protocol.codec.MysqlPacketDecoder;

/**
 * MySQL 连接池事件处理器，实现 Netty {@link ChannelPoolHandler}。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #channelCreated}：在新建 channel 上装配 pipeline
 *       ({@link MysqlPacketDecoder} + {@link MySqlHandler})，并创建 {@link MySqlSession}
 *       挂到 channel 的 {@link MySqlHandler#MYSQL_SESSION} attribute；</li>
 *   <li>{@link #channelAcquired} / {@link #channelReleased}：仅打印 debug 日志。</li>
 * </ul>
 * 每个 {@link MysqlServerConfig} 对应一个独立的 pool 与一个独立的 handler 实例。
 */
public class MysqlPoolHandler implements ChannelPoolHandler {

    private static final Logger log = LoggerFactory.getLogger( MysqlPoolHandler.class );

    /**
     * 本 handler 所属 server 的配置（host/port/账号密码/clusterId/容量参数等），
     * channelCreated 时透传给 {@link MySqlSession}。
     */
    private final MysqlServerConfig mysqlServerConfig;

    /**
     * @param mysqlServerConfig 所属 server 配置
     */
    public MysqlPoolHandler(MysqlServerConfig mysqlServerConfig) {
        this.mysqlServerConfig = mysqlServerConfig;
    }

    /**
     * channel 归还到连接池时回调，仅打印 debug 日志。
     *
     * @param channel 归还的 channel
     */
    @Override
    public void channelReleased(Channel channel) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug( "Channel[{}] Released!",channel );
        }
    }

    /**
     * channel 从连接池取出时回调，仅打印 debug 日志。
     *
     * @param channel 取出的 channel
     */
    @Override
    public void channelAcquired(Channel channel) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug( "Channel[{}] Acquired!",channel );
        }
    }

    /**
     * channel 新建时回调：装配 pipeline（解码器 + Handler），创建 {@link MySqlSession} 并绑定到
     * channel attribute，建立 channel↔session 的双向映射。
     *
     * @param channel 新建的 channel
     */
    @Override
    public void channelCreated(Channel channel) throws Exception {
        channel.pipeline().addLast( new MysqlPacketDecoder() );
        channel.pipeline().addLast( new MySqlHandler() );
        MySqlSession session = new MySqlSession( mysqlServerConfig, channel );
        channel.attr( MySqlHandler.MYSQL_SESSION ).set( session );
    }
}
