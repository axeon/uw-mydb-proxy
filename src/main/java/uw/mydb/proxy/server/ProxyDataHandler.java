package uw.mydb.proxy.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.protocol.packet.MySqlPacket;

import java.net.InetSocketAddress;

/**
 * 前端 channel 的核心业务处理器（{@link ChannelInboundHandlerAdapter}）。
 * <p>
 * 在 pipeline 中位于 {@link uw.mydb.proxy.protocol.codec.MysqlPacketDecoder} 之后，收到的 {@code msg} 已经是按 MySQL 协议切好的整包 ByteBuf。
 * 职责：
 * <ul>
 *   <li>连接建立时构造 {@link ProxySession}、注册到 {@link ProxySessionManager}，并向客户端发送握手包。</li>
 *   <li>读取每一条命令包，按是否登录与命令字节（CMD_QUERY / CMD_INIT_DB / CMD_PING / CMD_QUIT / CMD_PROCESS_KILL / CMD_STMT_* / CMD_HEARTBEAT）分发到 {@link ProxySession} 对应方法。</li>
 *   <li>读空闲超时（由 pipeline 中的 {@link IdleStateHandler} 触发）时主动关闭连接，清理半开连接。</li>
 *   <li>异常捕获时关闭连接，防止异常状态下的连接残留。</li>
 * </ul>
 * 线程安全：每个 channel 拥有独立的 handler 实例（{@code @Sharable} 未标注），由单一 EventLoop 线程串行调用，无需同步。
 *
 * @author axeon
 */
public class ProxyDataHandler extends ChannelInboundHandlerAdapter {

    /**
     * channel attribute key，用于在 channel 上挂载 {@link ProxySession} 实例。
     */
    public static final AttributeKey<ProxySession> MYDB_SESSION = AttributeKey.valueOf( "mydb.session" );

    private static final Logger logger = LoggerFactory.getLogger( ProxyDataHandler.class );

    /**
     * 连接建立回调。构造 ProxySession、解析客户端 IP/端口、绑定到 channel attribute、注册到 SessionManager，
     * 然后向客户端发送 Initial Handshake Packet 启动 MySQL 认证流程。
     *
     * @param ctx channel 上下文
     * @throws Exception 由父类逻辑抛出
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ProxySession session = new ProxySession( ctx );
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        session.setClientHost( address.getAddress().getHostAddress() );
        session.setClientPort( address.getPort() );
        ctx.channel().attr( MYDB_SESSION ).set( session );
        ProxySessionManager.put( ctx.channel().remoteAddress().toString(), session );
        session.sendHandshake( ctx );
        super.channelActive( ctx );
    }

    /**
     * 连接断开回调，从 {@link ProxySessionManager} 中移除该会话。
     *
     * @param ctx channel 上下文
     * @throws Exception 由父类逻辑抛出
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProxySession session = ctx.channel().attr( MYDB_SESSION ).get();
        ProxySessionManager.remove( ctx.channel().remoteAddress().toString() );
        super.channelInactive( ctx );
    }

    /**
     * 读取一条完整的 MySQL 命令包并分发处理。
     * <p>
     * 未登录则调用 {@link ProxySession#auth}；已登录则取 payload 第 1 字节作为命令类型（cmd byte）走 switch 分发。
     * 未识别的命令回写 {@code ER_UNKNOWN_COM_ERROR}。session 缺失（异常情况）直接关闭连接防泄漏。
     *
     * @param ctx channel 上下文
     * @param msg 由 {@link uw.mydb.proxy.protocol.codec.MysqlPacketDecoder} 切好的整包 ByteBuf
     * @throws Exception 由下游处理抛出
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //拿到session
        ProxySession session = ctx.channel().attr( MYDB_SESSION ).get();
        if (session == null) {
            logger.warn( "!!!发现错误来源的访问信息，来源:{}", ctx.channel().remoteAddress() );
            ctx.close();
            return;
        }
        //拿到消息
        ByteBuf buf = (ByteBuf) msg;
        session.updateLastResponseTime();
        if (!session.isLogon()) {
            //未登录状态，执行登录操作。
            session.auth( ctx, buf );
        } else {
            //已经登录
            byte type = buf.getByte( 4 );
            switch (type) {
                case MySqlPacket.CMD_INIT_DB:
                    session.initDB( ctx, buf );
                    break;
                case MySqlPacket.CMD_QUERY:
                    session.query( ctx, buf );
                    break;
                case MySqlPacket.CMD_PING:
                    session.ping( ctx );
                    break;
                case MySqlPacket.CMD_QUIT:
                    session.close( ctx );
                    break;
                case MySqlPacket.CMD_PROCESS_KILL:
                    session.kill( ctx, buf );
                    break;
                case MySqlPacket.CMD_STMT_PREPARE:
                    session.stmtPrepare( ctx, buf );
                    break;
                case MySqlPacket.CMD_STMT_EXECUTE:
                    session.stmtExecute( ctx, buf );
                    break;
                case MySqlPacket.CMD_STMT_CLOSE:
                    session.stmtClose( ctx, buf );
                    break;
                case MySqlPacket.CMD_HEARTBEAT:
                    session.heartbeat( ctx, buf );
                    break;
                default:
                    session.onProxyFailMessage( ctx, MySqlErrorCode.ER_UNKNOWN_COM_ERROR, "Unknown command" );
                    session.onFinish();
                    break;
            }
        }
        super.channelRead( ctx, msg );
    }

    /**
     * 异常捕获：记录日志后关闭前端连接，防止异常状态下的连接残留导致后续状态错乱。
     *
     * @param ctx   channel 上下文
     * @param cause 异常
     * @throws Exception 由父类逻辑抛出
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error( "!!!发现异常[{}]，已关闭连接来源:{}", cause.getMessage(), ctx.channel().remoteAddress(), cause );
        ctx.close();
    }

    /**
     * 处理IdleState事件，读空闲超时表示客户端可能已半开/断开（拔网线/NAT超时），
     * 主动关闭前端连接，触发channelInactive清理后端资源。
     *
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                logger.warn( "客户端连接读空闲超时，关闭连接: {}", ctx.channel().remoteAddress() );
                ctx.close();
                return;
            }
        }
        super.userEventTriggered( ctx, evt );
    }


}
