package uw.mydb.proxy.mysql;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;

/**
 * 后端 MySQL channel 的 Netty 入站处理器，桥接 Netty 事件与 {@link MySqlSession}。
 *
 * <p>职责：
 * <ul>
 *   <li>从 channel attribute 取出 {@link MySqlSession}，把后端响应 ByteBuf 委托给
 *       {@link MySqlSession#handleResponse} 处理；</li>
 *   <li>channel 断开时调用 {@link MySqlSession#trueClose()} 让 session 进入 CLOSED 终态并归还 pool；</li>
 *   <li>捕获异常时回调 {@link MySqlSession#failMessage} 通知前端，并强制关闭 session。</li>
 * </ul>
 * 实例由 {@link MysqlPoolHandler#channelCreated} 在 pipeline 上挂载，每个 channel 一个独立 Handler 实例。
 *
 * @author axeon
 */
public class MySqlHandler extends ChannelInboundHandlerAdapter {

    /**
     * channel attribute key，存储该 channel 对应的 {@link MySqlSession} 实例。
     * 由 {@link MysqlPoolHandler#channelCreated} 在建连时 set。
     */
    public static final AttributeKey<MySqlSession> MYSQL_SESSION = AttributeKey.valueOf( "mysql.session" );
    private static final Logger logger = LoggerFactory.getLogger( MySqlHandler.class );

    /**
     * channel 断开时回调：取出 session 并调用 {@link MySqlSession#trueClose()} 进入终态、归还连接池，
     * 然后传播事件给后续 handler。
     *
     * @param ctx Netty 上下文
     * @throws Exception 传播链上抛出的异常
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        if (session != null) {
            session.trueClose();
        }
        super.channelInactive( ctx );
    }

    /**
     * 收到后端响应：取出 session 委托给 {@link MySqlSession#handleResponse} 处理，再传播事件。
     * 若 session 缺失（异常状态）直接关闭 channel。
     *
     * @param ctx Netty 上下文
     * @param msg 后端响应，类型为 {@link ByteBuf}
     * @throws Exception 处理链上抛出的异常
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        if (session == null) {
            logger.error( "!!!MySqlSession未获取到!!!" );
            ctx.close();
            return;
        }
        //拿到消息
        session.handleResponse( ctx, (ByteBuf) msg );
        super.channelRead( ctx, msg );
    }

    /**
     * 异常捕获：传播异常后通知 session 失败原因（ERR_CONN_NOT_ALIVE），并强制关闭 session 避免悬挂。
     *
     * @param ctx   Netty 上下文
     * @param cause 捕获到的异常
     * @throws Exception 传播链上抛出的异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught( ctx, cause );
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        if (session != null) {
            session.failMessage( MySqlErrorCode.ERR_CONN_NOT_ALIVE, cause.getMessage() );
            session.trueClose();
        }
    }

}
