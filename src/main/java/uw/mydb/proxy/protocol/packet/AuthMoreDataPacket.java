package uw.mydb.proxy.protocol.packet;


import io.netty.buffer.ByteBuf;

/**
 * AuthMoreDataPacket
 **/
public class AuthMoreDataPacket extends MySqlPacket {

    public byte status;

    public byte data;

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte( status );
        buf.writeByte( data );
    }

    @Override
    protected void read(ByteBuf buf) {
        status = buf.readByte();
        data = buf.readByte();
    }

}
