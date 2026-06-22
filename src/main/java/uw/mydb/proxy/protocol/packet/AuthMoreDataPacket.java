package uw.mydb.proxy.protocol.packet;


import io.netty.buffer.ByteBuf;

/**
 * MySQL AuthMoreData 包（服务端 -> 客户端）：caching_sha2_password 等插件在快速认证失败时，
 * 通过本包携带额外认证数据（如 "perform full authentication" 标志 0x04），触发客户端走完整认证流程。
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
