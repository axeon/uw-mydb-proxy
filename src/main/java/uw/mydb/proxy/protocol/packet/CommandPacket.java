package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.util.ByteBufUtils;

import java.nio.charset.StandardCharsets;

/**
 * MySQL COM_xxx 命令包（客户端 -> 服务端），payload 结构：1 字节 command + n 字节 arg。
 * <p>
 * command 取值见 {@link MySqlPacket} 中的 CMD_* 常量；最常见是 {@link MySqlPacket#CMD_QUERY}（0x03），
 * 此时 arg 为 SQL 文本（不包含结尾的分号）。
 * <p>
 * arg 字段不是 null-terminated 字符串，其长度由包头决定；MySQL 客户端接收时会自行追加 '\0'。
 *
 * <pre>
 * Bytes         Name
 * -----         ----
 * 1             command
 * n             arg
 * </pre>
 *
 * @author axeon
 */
public class CommandPacket extends MySqlPacket {

    /**
     * 命令字节，默认 {@link MySqlPacket#CMD_QUERY}。取值见 {@link MySqlPacket} 中的 CMD_* 常量。
     */
    public byte command = MySqlPacket.CMD_QUERY;

    /**
     * 命令参数。COM_QUERY 时为 SQL 文本；COM_INIT_DB 时为 database 名；其他命令可能为空。
     */
    public String arg;


    /**
     * 从 ByteBuf 读取 payload：1 字节 command + 剩余全部作为 arg（EOF 读取）。
     *
     * @param buf 源 ByteBuf（position 在 payload 起始）
     */
    @Override
    protected void read(ByteBuf buf) {
        command = buf.readByte();
        arg = ByteBufUtils.readStringWithEof(buf);
    }

    /**
     * 把 payload 写入 ByteBuf：command 字节 + arg 的 UTF-8 字节。
     *
     * @param buf 目标 ByteBuf
     */
    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte(command);
        buf.writeBytes(arg.getBytes(StandardCharsets.UTF_8));
    }

}

