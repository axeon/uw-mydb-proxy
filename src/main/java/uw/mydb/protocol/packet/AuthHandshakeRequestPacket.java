package uw.mydb.protocol.packet;


import io.netty.buffer.ByteBuf;
import uw.mydb.protocol.util.MySQLCapability;
import uw.mydb.util.ByteBufUtils;

/**
 * MySql握手包
 *
 * @author axeon
 */
public class AuthHandshakeRequestPacket extends MySqlPacket {

    private static final byte[] FILLER_10 = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    public byte protocolVersion;
    public String serverVersion;
    public long connectionId;
    public byte[] authPluginDataPartOne;
    public long serverCapabilities;
    public boolean hasPartTwo = true;
    // UTF8MB4
    public byte serverCharsetIndex = (byte) 255;
    // SERVER_STATUS_AUTOCOMMIT
    public int serverStatus = 0x0002;
    public byte[] authPluginDataPartTwo;
    // 有插件的话，总长度必是21
    public int authPluginDataLen = 21;
    public String authPluginName;

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte( protocolVersion );
        ByteBufUtils.writeStringWithNull( buf, serverVersion );
        ByteBufUtils.writeUB4( buf, connectionId );
        ByteBufUtils.writeBytesWithNull( buf, authPluginDataPartOne );
        ByteBufUtils.writeUB2( buf, MySQLCapability.getLower2Bytes( serverCapabilities ) );
        if (hasPartTwo) {
            buf.writeByte( serverCharsetIndex );
            ByteBufUtils.writeUB2( buf, serverStatus );
            ByteBufUtils.writeUB2( buf, MySQLCapability.getUpper2Bytes( serverCapabilities ) );
            if (MySQLCapability.isClientPluginAuth( serverCapabilities )) {
                buf.writeByte( (byte) authPluginDataLen );
            } else {
                buf.writeByte( (byte) 0 );
            }
            buf.writeBytes( FILLER_10 );
            if (MySQLCapability.isClientSecureConnection( serverCapabilities )) {
                ByteBufUtils.writeBytesWithNull( buf, authPluginDataPartTwo );
            }
            if (MySQLCapability.isClientPluginAuth( serverCapabilities )) {
                ByteBufUtils.writeStringWithNull( buf, this.authPluginName );
            }
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        protocolVersion = buf.readByte();
        serverVersion = ByteBufUtils.readStringWithNull( buf );
        connectionId = ByteBufUtils.readUB4( buf );
        authPluginDataPartOne = ByteBufUtils.readBytesWithNull( buf );
        //低8位数据
        serverCapabilities = ByteBufUtils.readUB2( buf ) & 0x0000ffff;
        if (buf.isReadable()) {
            hasPartTwo = true;
            serverCharsetIndex = buf.readByte();
            serverStatus = ByteBufUtils.readUB2( buf );
            //读取高2字节数据
            long up2bytes = ByteBufUtils.readUB2( buf ) << 16;
            serverCapabilities |= up2bytes;
            if (MySQLCapability.isClientPluginAuth( serverCapabilities )) {
                byte b = buf.readByte();
                authPluginDataLen = Byte.toUnsignedInt( b );
            } else {
                buf.skipBytes( 1 );
            }
            buf.skipBytes( 10 );
            if (MySQLCapability.isClientSecureConnection( serverCapabilities )) {
                authPluginDataPartTwo = ByteBufUtils.readBytesWithNull( buf );
            }
            if (MySQLCapability.isClientPluginAuth( serverCapabilities )) {
                authPluginName = ByteBufUtils.readStringWithNull( buf );
            }
        }
    }

}
