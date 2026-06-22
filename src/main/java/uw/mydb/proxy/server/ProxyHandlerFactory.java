package uw.mydb.proxy.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import uw.mydb.proxy.protocol.codec.MysqlPacketDecoder;

import java.util.concurrent.TimeUnit;

/**
 * 前端 channel 初始化器（{@link ChannelInitializer}），由 {@link uw.mydb.proxy.server.ProxyServer} 注册到 ServerBootstrap。
 * <p>
 * 为每条新接入的客户端 channel 组装 pipeline：
 * <ol>
 *   <li>{@link IdleStateHandler}：读空闲超时检测（readerIdle = {@link #READER_IDLE_SECONDS}，写/all 不限）。</li>
 *   <li>{@link uw.mydb.proxy.protocol.codec.MysqlPacketDecoder}：MySQL 协议半包/粘包拆分。</li>
 *   <li>{@link ProxyDataHandler}：握手 + 命令分发核心业务处理器。</li>
 * </ol>
 *
 * @author axeon
 */
public class ProxyHandlerFactory extends ChannelInitializer<SocketChannel> {

    /**
     * 读空闲超时（秒）。超过 2 小时无任何数据收发的连接视为半开/死连接，主动关闭释放前后端资源。
     * 设为 2 小时是为了不误杀长时间运行的查询（后端 connBusyTimeout 默认 1 小时），仅清理真正静默断开的半开连接。
     */
    private static final int READER_IDLE_SECONDS = 2 * 60 * 60;


    /**
     * 为新接入的 channel 组装 pipeline。
     *
     * @param ch 新接入的 SocketChannel
     */
    @Override
    protected void initChannel(SocketChannel ch) {
//        ch.pipeline().addLast( new LoggingHandler(LogLevel.INFO));
        ch.pipeline().addLast(new IdleStateHandler(READER_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS));
        ch.pipeline().addLast(new MysqlPacketDecoder());
        ch.pipeline().addLast(new ProxyDataHandler());
    }
}