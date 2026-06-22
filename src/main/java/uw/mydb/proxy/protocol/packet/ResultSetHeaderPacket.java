package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.util.ByteBufUtils;

/**
 * MySQL 结果集头包（服务端 -> 客户端）：SELECT 等返回结果集时，本包作为整个结果集序列的第一个包，
 * 携带列数（field_count）。后续序列为：N 个 {@link ResultSetFieldPacket} -> 1 个 {@link EOFPacket}
 * -> M 个 {@link ResultSetRowDataPacket} -> 1 个 {@link EOFPacket}（或 OK 包，CLIENT_DEPRECATE_EOF 时）。
 *
 * <pre>
 * Bytes                        Name
 * -----                        ----
 * 1-9   (Length-Coded-Binary)  field_count
 * 1-9   (Length-Coded-Binary)  extra (optional)
 * </pre>
 *
 * @author axeon
 */
public class ResultSetHeaderPacket extends MySqlPacket {
    /**
     * 列数（结果集字段数）。
     */
    public int fieldCount;
    /**
     * 附加信息（如 SELECT FOUND_ROWS() 的行数），可选。
     */
    public long extra;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void read(ByteBuf buf) {
        fieldCount = (int) ByteBufUtils.readLenEncInt(buf);
        if (buf.readableBytes() > 0) {
            this.extra = ByteBufUtils.readLenEncInt(buf);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeLenEncInt(buf, fieldCount);
        if (extra > 0) {
            ByteBufUtils.writeLenEncInt(buf, extra);
        }
    }

}
