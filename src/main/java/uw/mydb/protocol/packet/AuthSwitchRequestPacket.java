package uw.mydb.protocol.packet;


import io.netty.buffer.ByteBuf;
import uw.mydb.util.ByteBufUtils;

/**
 * AuthSwitchRequest
 * https://dev.mysql.com/doc/internals/en/connection-phase-packets.html#packet-Protocol::AuthSwitchRequest
 *
 * <pre>
 *      1              [fe]
 *      string[NUL]    plugin name
 *      string[EOF]    auth plugin data
 * </pre>
 **/
public class AuthSwitchRequestPacket extends MySqlPacket {
    public byte status;
    public String authPluginName;
    public byte[] authPluginData;

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte( status );
        if (authPluginName == null) {
            buf.writeByte( (byte) 0 );
        } else {
            ByteBufUtils.writeBytesWithNull( buf, authPluginName.getBytes() );
        }
        if (authPluginData == null) {
            buf.writeByte( (byte) 0 );
        } else {
            buf.writeBytes( authPluginData );
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        status = buf.readByte();
        authPluginName = ByteBufUtils.readStringWithNull( buf );
        authPluginData = ByteBufUtils.readBytesWithEof( buf );
    }


}
