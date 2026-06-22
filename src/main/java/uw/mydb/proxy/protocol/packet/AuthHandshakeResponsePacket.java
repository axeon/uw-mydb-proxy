package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.protocol.constant.MySQLCapability;
import uw.mydb.proxy.util.ByteBufUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MySQL Handshake Response（客户端 -> 服务端）：客户端收到 Initial Handshake Packet 后回复，
 * 携带能力位、最大包大小、字符集、用户名、加密后的密码、目标 database、认证插件名、连接属性等，
 * 用于服务端校验身份与 schema 选择。
 *
 * <pre>
 * Bytes                        Name
 * -----                        ----
 * 4                            client_flags
 * 4                            max_packet_size
 * 1                            charset_number
 * 23                           (filler) always 0x00...
 * n (Null-Terminated String)   user
 * n (Length Coded Binary)      scramble_buff (1 + x bytes)
 * n (Null-Terminated String)   database name (optional)
 * </pre>
 *
 * @author axeon
 */
public class AuthHandshakeResponsePacket extends MySqlPacket {
    private static final byte[] RESERVED = new byte[23];

    public long clientCapability;
    public long maxPacketSize;
    public int charsetIndex;
    public String username;

    public byte[] password;

    public String database;
    public String authPluginName;
    public Map<String, String> clientConnectAttrs;

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeUB4( buf, clientCapability );
        ByteBufUtils.writeUB4( buf, maxPacketSize );
        buf.writeByte( (byte) charsetIndex );
        buf.writeBytes( RESERVED );
        if (username == null) {
            buf.writeByte( (byte) 0 );
        } else {
            ByteBufUtils.writeStringWithNull( buf, username );
        }
        if (password == null) {
            buf.writeByte( (byte) 0 );
        } else {
            ByteBufUtils.writeBytesWithLenEnc( buf, password );
        }

        if (database == null) {
            buf.writeByte( (byte) 0 );
        }else{
            ByteBufUtils.writeStringWithNull( buf, database );
        }
        if (authPluginName == null) {
            buf.writeByte( (byte) 0 );
        }else{
            ByteBufUtils.writeStringWithNull( buf, authPluginName );
        }
        if (MySQLCapability.isClientConnectAttrs( clientCapability ) && clientConnectAttrs != null && !clientConnectAttrs.isEmpty()) {
            int kvAllLength = 0;
            for (Map.Entry<String, String> item : clientConnectAttrs.entrySet()) {
                kvAllLength += ByteBufUtils.calcLenEncDataLength( item.getKey().getBytes() );
                kvAllLength += ByteBufUtils.calcLenEncDataLength( item.getValue().getBytes() );
            }
            ByteBufUtils.writeLenEncInt( buf, kvAllLength );
            for (Map.Entry<String, String> item : clientConnectAttrs.entrySet()) {
                ByteBufUtils.writeStringWithLenEnc( buf, item.getKey() );
                ByteBufUtils.writeStringWithLenEnc( buf, item.getValue() );
            }
        }
    }

    @Override
    protected void read(ByteBuf buf) {
        clientCapability = ByteBufUtils.readUB4( buf );
        maxPacketSize = ByteBufUtils.readUB4( buf );
        charsetIndex = (buf.readByte() & 0xff);
        //跳过filler
        buf.skipBytes( RESERVED.length );
        username = ByteBufUtils.readStringWithNull( buf );
        if (MySQLCapability.isClientPluginAuthLenencClientData( clientCapability )) {
            password = ByteBufUtils.readBytesWithLenEnc( buf );
        } else if ((MySQLCapability.isClientSecureConnection( clientCapability ))) {
            password = ByteBufUtils.readBytesWithLenEnc( buf );
        } else {
            password = ByteBufUtils.readBytesWithNull( buf );
        }

        if (MySQLCapability.isClientConnectWithDb( clientCapability )) {
            database = ByteBufUtils.readStringWithNull( buf );
        }

        if (MySQLCapability.isClientPluginAuth( clientCapability )) {
            authPluginName = ByteBufUtils.readStringWithNull( buf );
        }

        if (MySQLCapability.isClientConnectAttrs( clientCapability ) && buf.isReadable()) {
            long kvAllLength = ByteBufUtils.readLenEncInt( buf );
            //用buf剩余可读字节数作为上界，防止恶意客户端声明超大kvAllLength导致死循环/OOM。
            long remaining = buf.readableBytes();
            if (kvAllLength > 0 && kvAllLength <= remaining) {
                clientConnectAttrs = new HashMap<>();
                int count = 0;
                while (count < kvAllLength && buf.isReadable()) {
                    String k = ByteBufUtils.readStringWithLenEnc( buf );
                    String v = ByteBufUtils.readStringWithLenEnc( buf );
                    count += ByteBufUtils.calcLenEncDataLength( k.getBytes() );
                    count += ByteBufUtils.calcLenEncDataLength( v.getBytes() );
                    clientConnectAttrs.put( k, v );
                }
            }
        }
    }

}
