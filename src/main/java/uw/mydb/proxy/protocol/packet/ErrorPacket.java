package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.util.ByteBufUtils;

import java.nio.charset.StandardCharsets;

/**
 * MySQL ERROR 包（服务端 -> 客户端）：命令执行失败时返回。
 * <p>
 * payload 结构：1 字节 header(0xFF) + 2 字节 errorNo + 1 字节 '#' 标记 + 5 字符 sqlstate + n 字节 message。
 *
 * <pre>
 * Bytes                       Name
 * -----                       ----
 * 1                           field_count, always = 0xff
 * 2                           error_no
 * 1                           (sqlstate marker), always '#'
 * 5                           sqlstate (5 characters)
 * n                           message
 * </pre>
 *
 * @author axeon
 */
public class ErrorPacket extends MySqlPacket {

    /**
     * SQLSTATE 标记字符 '#'。
     */
    private static final byte SQLSTATE_MARKER = (byte) '#';
    /**
     * 默认 SQLSTATE（proxy 自身产生的错误使用 "UW000"）。
     */
    private static final byte[] DEFAULT_SQLSTATE = "UW000".getBytes();

    /**
     * 包类型标识，固定为 {@link #PACKET_ERROR}（0xFF）。
     */
    public byte packetType = PACKET_ERROR;
    /**
     * MySQL 错误号（如 1045 Access denied）。
     */
    public int errorNo;
    /**
     * SQLSTATE 标记字节。
     */
    public byte mark = SQLSTATE_MARKER;
    /**
     * 5 字符 SQLSTATE，默认 "UW000"。
     */
    public byte[] sqlState = DEFAULT_SQLSTATE;
    /**
     * 人类可读的错误消息（UTF-8）。
     */
    public String message;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void read(ByteBuf buf) {
        packetType = buf.readByte();
        errorNo = ByteBufUtils.readUB2(buf);
        if (buf.readableBytes() > 0 && buf.getByte(buf.readerIndex()) == SQLSTATE_MARKER) {
            buf.skipBytes(1); // skip '#'
            sqlState = new byte[5];
            buf.readBytes(sqlState);
        }
        if (buf.readableBytes() > 0) {
            message = ByteBufUtils.readStringWithEof(buf);
        } else {
            message = "";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte(packetType);
        ByteBufUtils.writeUB2(buf, errorNo);
        buf.writeByte(mark);
        buf.writeBytes(sqlState);
        if (message != null) {
            buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
        }
    }

}
