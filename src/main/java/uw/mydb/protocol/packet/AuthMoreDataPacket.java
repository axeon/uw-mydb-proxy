package uw.mydb.protocol.packet;


import io.netty.buffer.ByteBuf;
import uw.mydb.util.ByteBufUtils;

/**
 * AuthMoreDataPacket
 **/
public class AuthMoreDataPacket extends MySqlPacket {

    public byte status;

    public String data;

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte( status );
        if (data == null) {
            buf.writeByte( (byte) 0 );
        } else {
            ByteBufUtils.writeStringWithNull( buf, data );
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        status = buf.readByte();
        data = ByteBufUtils.readStringWithNull( buf );
    }

}
