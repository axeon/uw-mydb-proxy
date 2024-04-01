package uw.mydb.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.util.ByteBufUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * From server to client. One packet for each row in the result set.
 * Bytes                   Name
 * -----                   ----
 * n (Length Coded String) (column value)
 * ...
 * <p>
 * (column value):         The data in the column, as a character string.
 * If a column is defined as non-character, the
 * server converts the value into a character
 * before sending it. Since the value is a Length
 * Coded String, a NULL can be represented with a
 * single byte containing 251(see the description
 * of Length Coded Strings in section "Elements" above).
 *
 * @author axeon
 */
public class ResultSetRowDataPacket extends MySqlPacket {

    private static final byte NULL_MARK = (byte) 251;

    private static final byte EMPTY_MARK = (byte) 0;

    public List<byte[]> fieldValues;

    public int fieldCount;

    public ResultSetRowDataPacket() {
    }

    public ResultSetRowDataPacket(int fieldCount) {
        this.fieldCount = fieldCount;
        this.fieldValues = new ArrayList<byte[]>(fieldCount);
    }

    public void addField(byte[] value) {
        // 这里应该修改value
        fieldValues.add(value);
        fieldCount++;
    }

    @Override
    protected void read(ByteBuf buf) {
        for (int i = 0; i < fieldCount; i++) {
            fieldValues.add(ByteBufUtils.readBytesWithLenEnc(buf));
        }
    }

    @Override
    protected void write(ByteBuf buf) {
        for (int i = 0; i < fieldCount; i++) {
            byte[] fv = fieldValues.get(i);
            if (fv == null) {
                buf.writeByte( ResultSetRowDataPacket.NULL_MARK);
            } else if (fv.length == 0) {
                buf.writeByte( ResultSetRowDataPacket.EMPTY_MARK);
            } else {
                ByteBufUtils.writeBytesWithLenEnc(buf, fv);
            }
        }
    }
}
