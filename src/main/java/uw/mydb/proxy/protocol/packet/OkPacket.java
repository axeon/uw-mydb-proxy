package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import uw.mydb.proxy.util.ByteBufUtils;

/**
 * MySQL OK 包（服务端 -> 客户端）：用于确认一条非结果集命令成功完成，或在结果集末尾替代 EOF（CLIENT_DEPRECATE_EOF）。
 * <p>
 * payload 结构：1 字节 header(0x00) + lenenc affectedRows + lenenc insertId + 2 字节 serverStatus +
 * 2 字节 warningCount + 可选 lenenc message。
 *
 * <pre>
 * Bytes                 Name
 * -----                 ----
 * 1                     field_count, always = 0x00
 * lenenc                affected_rows
 * lenenc                last_insert_id
 * 2                     server_status
 * 2                     warning_count
 * lenenc                message (optional)
 * </pre>
 *
 * @author axeon
 */
public class OkPacket extends MySqlPacket {
    /**
     * 预序列化的最小 OK 包字节（用于 PING 等简单确认），packetId=1。
     */
    public static final byte[] OK = new byte[]{7, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0};
    /**
     * 预序列化的认证成功 OK 包字节，packetId=2（认证序列的下一序号）。
     */
    public static final byte[] AUTH_OK = new byte[]{7, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0};

    /**
     * 包类型标识，固定为 {@link #PACKET_OK}（0x00）。
     */
    public byte packetType = PACKET_OK;
    /**
     * 受影响行数（INSERT/UPDATE/DELETE）。
     */
    public long affectedRows;
    /**
     * INSERT 产生的 AUTO_INCREMENT 值。
     */
    public long insertId;
    /**
     * 服务端状态位（如 SERVER_STATUS_AUTOCOMMIT=0x02）。
     */
    public int serverStatus;
    /**
     * 警告计数。
     */
    public int warningCount;

    /**
     * 附加消息（可选），如 "Records: 100"。
     */
    public byte[] message;

    /**
     * 向 channel 直接写一条预序列化的 OK 包（packetId=1，status=0x02 autocommit）。
     *
     * @param ctx channel 上下文
     */
    public static void writeOkToChannel(ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ctx.alloc().buffer( OkPacket.OK.length ).writeBytes( OkPacket.OK );
        ctx.writeAndFlush( byteBuf );
    }

    /**
     * 向 channel 直接写一条预序列化的 OK 包，使用 channel 的 allocator。
     *
     * @param channel 目标 channel
     */
    public static void writeOkToChannel(Channel channel) {
        ByteBuf byteBuf = channel.alloc().buffer( OkPacket.OK.length ).writeBytes( OkPacket.OK );
        channel.writeAndFlush( byteBuf );
    }

    /**
     * 写一条认证成功 OK 包（packetId 默认 2，对应认证序列）。
     *
     * @param ctx channel 上下文
     */
    public static void writeAuthOkToChannel(ChannelHandlerContext ctx) {
        writeAuthOkToChannel( ctx, (byte) 2 );
    }

    /**
     * 写一条认证成功 OK 包，可指定 packetId（serverStatus 固定 0x02 autocommit）。
     *
     * @param ctx      channel 上下文
     * @param packetId 自定义 packetId
     */
    public static void writeAuthOkToChannel(ChannelHandlerContext ctx, byte packetId) {
        OkPacket okPacket = new OkPacket();
        okPacket.packetId = packetId;
        okPacket.serverStatus = 0x02;
        ByteBuf buf = ctx.alloc().buffer( 16 );
        okPacket.writePayLoad( buf );
        ctx.writeAndFlush( buf );
    }

    /**
     * 判断 serverStatus 是否包含指定状态位。
     *
     * @param flag 状态位
     * @return true 表示该位被置位
     */
    public boolean hasStatusFlag(long flag) {
        return ((this.serverStatus & flag) == flag);
    }

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeLenEncInt( buf, packetType );
        ByteBufUtils.writeLenEncInt( buf, affectedRows );
        ByteBufUtils.writeLenEncInt( buf, insertId );
        ByteBufUtils.writeUB2( buf, serverStatus );
        ByteBufUtils.writeUB2( buf, warningCount );
        if (message != null) {
            ByteBufUtils.writeBytesWithLenEnc( buf, message );
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        packetType = buf.readByte();
        affectedRows = ByteBufUtils.readLenEncInt( buf );
        insertId = ByteBufUtils.readLenEncInt( buf );
        serverStatus = ByteBufUtils.readUB2( buf );
        warningCount = ByteBufUtils.readUB2( buf );
        if (buf.readableBytes() > 0) {
            message = ByteBufUtils.readBytesWithLenEnc( buf );
        }
    }


}
