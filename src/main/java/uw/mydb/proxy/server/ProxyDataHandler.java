package uw.mydb.proxy.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.protocol.constant.MySqlErrorCode;
import uw.mydb.proxy.protocol.packet.MySqlPacket;

import java.net.InetSocketAddress;

/**
 * 前端Proxy数据处理器。
 *
 * @author axeon
 */
public class ProxyDataHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<ProxySession> MYDB_SESSION = AttributeKey.valueOf( "mydb.session" );

    private static final Logger logger = LoggerFactory.getLogger( ProxyDataHandler.class );

    /**
     * 加入session。
     *
     * @param ctx
     * @throws Exception
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
     * 退出session。
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProxySession session = ctx.channel().attr( MYDB_SESSION ).get();
        ProxySessionManager.remove( ctx.channel().remoteAddress().toString() );
        super.channelInactive( ctx );
    }

    /**
     * 接收到用户消息。
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //拿到session
        ProxySession session = ctx.channel().attr( MYDB_SESSION ).get();
        if (session == null) {
            logger.warn( "!!!发现错误来源的访问信息，来源:{}", ctx.channel().remoteAddress() );
            ctx.close();
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
     * 异常捕获。
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error( "!!!发现异常[{}]，已关闭连接来源:{}", cause.getMessage(), ctx.channel().remoteAddress(), cause );
        ctx.close();
    }


}
