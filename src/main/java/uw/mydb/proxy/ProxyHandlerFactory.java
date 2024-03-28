package uw.mydb.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import uw.mydb.protocol.codec.MysqlPacketDecoder;

/**
 * 代理端handler工厂
 *
 * @author axeon
 */
public class ProxyHandlerFactory extends ChannelInitializer<SocketChannel> {


    @Override
    protected void initChannel(SocketChannel ch) {
//        ch.pipeline().addLast( new LoggingHandler(LogLevel.INFO));
        ch.pipeline().addLast(new MysqlPacketDecoder());
        ch.pipeline().addLast(new ProxyDataHandler());
    }
}