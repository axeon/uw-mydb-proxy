package uw.mydb.mysql2;


import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.protocol.codec.MysqlPacketDecoder;
import uw.mydb.vo.MysqlServerConfig;

public class MysqlPoolHandler implements ChannelPoolHandler {

    private static final Logger log = LoggerFactory.getLogger( MysqlPoolHandler.class );
    /**
     * mysql server 配置。
     */
    private MysqlServerConfig mysqlServerConfig;

    public MysqlPoolHandler(MysqlServerConfig mysqlServerConfig) {
        this.mysqlServerConfig = mysqlServerConfig;
    }

    @Override
    public void channelReleased(Channel channel) throws Exception {
        log.info( "channelReleased" );
    }

    @Override
    public void channelAcquired(Channel channel) throws Exception {
        log.info( "channelAcquired" );
    }

    @Override
    public void channelCreated(Channel channel) throws Exception {
        channel.pipeline().addLast( new MysqlPacketDecoder() );
        channel.pipeline().addLast( new MySqlHandler() );
        MySqlSession session = new MySqlSession( mysqlServerConfig, channel );
        channel.attr( MySqlHandler.MYSQL_SESSION ).set( session );
        //        channel.writeAndFlush( null ).s
        log.info( "channelCreated" );
    }
}
