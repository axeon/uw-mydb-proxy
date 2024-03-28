package uw.mydb.mysql2;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import uw.mydb.mysql.MySqlDataHandler;
import uw.mydb.protocol.codec.MysqlPacketDecoder;

/**
 * Mysql handler工厂
 *
 * @author axeon
 */
public class MySqlHandlerFactory extends ChannelInitializer<SocketChannel> {


    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
//        ch.pipeline().addLast( new LoggingHandler(LogLevel.INFO));
        ch.pipeline().addLast(new MysqlPacketDecoder());
        ch.pipeline().addLast(new MySqlDataHandler());
    }

}