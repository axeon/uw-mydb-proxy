package uw.mydb.protocol.packet;


import io.netty.buffer.ByteBuf;
import uw.mydb.util.ByteBufUtils;

/**
 * AuthSwitchResponsePacket
 * https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_connection_phase_packets_protocol_auth_switch_response.html
 **/
public class AuthSwitchResponsePacket extends MySqlPacket {

    public byte[] data;

    @Override
    protected void write(ByteBuf buf) {
        if (data == null) {
            buf.writeByte( (byte) 0 );
        } else {
            ByteBufUtils.writeBytesWithNull( buf, data );
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        data = ByteBufUtils.readBytesWithEof( buf );
    }


}
