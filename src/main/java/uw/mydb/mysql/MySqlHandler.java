package uw.mydb.mysql;

import io.netty.buffer.ByteBuf;
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
        session.handleResponse( ctx, (ByteBuf) msg );
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
