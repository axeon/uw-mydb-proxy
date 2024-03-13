package uw.mydb.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.util.ByteBufUtils;

/**
 * From server to client in response to command, if error.
 * Bytes                       Name
 * -----                       ----
 * 1                           field_count, always = 0xff
 * 2                           errorNo
 * 1                           (sqlstate marker), always '#'
 * 5                           sqlstate (5 characters)
 * n                           message
 *
 * @author axeon
 */
public class ErrorPacket extends MySqlPacket {

    private static final byte SQLSTATE_MARKER = (byte) '#';
    private static final byte[] DEFAULT_SQLSTATE = "UW000".getBytes();

    public byte packetType = PACKET_ERROR;
    public int errorNo;
    public byte mark = SQLSTATE_MARKER;
    public byte[] sqlState = DEFAULT_SQLSTATE;
    public String message;

    @Override
    protected void read(ByteBuf buf) {
        packetType = buf.readByte();
        errorNo = ByteBufUtils.readUB2(buf);
        message = ByteBufUtils.readStringWithEof(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte(packetType);
        ByteBufUtils.writeUB2(buf, errorNo);
        buf.writeByte(mark);
        buf.writeBytes(sqlState);
        if (message != null) {
            buf.writeBytes(message.getBytes());
        }
    }

}
