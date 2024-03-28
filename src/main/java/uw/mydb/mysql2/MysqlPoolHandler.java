package uw.mydb.mysql2;


import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPoolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MysqlPoolHandler implements ChannelPoolHandler {

    private static final Logger log = LoggerFactory.getLogger( MysqlPoolHandler.class );

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
//        channel.writeAndFlush( null ).s
        log.info( "channelCreated" );
    }
}
