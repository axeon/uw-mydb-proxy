package uw.mydb.mysql;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理mysql端的数据交互。
 *
 * @author axeon
 */
public class MySqlHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<MySqlSession> MYSQL_SESSION = AttributeKey.valueOf( "mysql.session" );
    private static final Logger logger = LoggerFactory.getLogger( MySqlHandler.class );

    /**
     * 退出session。
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info( "channelInactive" );
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        session.trueClose();
        super.channelInactive( ctx );
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        if (session == null) {
            logger.error( "!!!MySqlSession未获取到!!!" );
            ctx.close();
            return;
        }
        //拿到消息
        ByteBuf buf = (ByteBuf) msg;
        switch (session.getSessionState()) {
            case MySqlSession.SESSION_INIT:
                //初始阶段，此时需要发送验证包
                logger.info( "收到mysql初始化信息" );
                session.handleHandshake( ctx, buf );
                break;
            case MySqlSession.SESSION_AUTH:
                logger.info( "收到mysql验证结果信息" );
                //验证阶段。
                session.handleAuthResponse( ctx, buf );
                break;
            case MySqlSession.SESSION_NORMAL:
                //闲置idle接收到的信息
                logger.warn( "!!!状态[NORMAL]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                break;
            case MySqlSession.SESSION_USING:
                //开始接受业务数据。
                session.handleCommandResponse( ctx, buf );
                break;
            case MySqlSession.SESSION_CLOSED:
                //验证失败信息，直接关闭链接吧。
                logger.warn( "!!!状态[REMOVED]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                ctx.close();
                break;
            default:
                logger.warn( "!!!状态[" + session.getSessionState() + "]未处理信息:" + ByteBufUtil.prettyHexDump( buf ) );
                ctx.close();
                //这时候基本上就是登录失败了，直接关连接就好了。
        }
        super.channelRead( ctx, msg );
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught( ctx, cause );
        MySqlSession session = ctx.channel().attr( MYSQL_SESSION ).get();
        ctx.close();
        session.trueClose();
    }


}
