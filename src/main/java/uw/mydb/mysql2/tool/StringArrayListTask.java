package uw.mydb.mysql2.tool;

import io.netty.buffer.ByteBuf;
import uw.mydb.protocol.packet.ErrorPacket;
import uw.mydb.protocol.packet.ResultSetHeaderPacket;
import uw.mydb.protocol.packet.RowDataPacket;

import java.util.ArrayList;

/**
 * 所有仅返回单行列表的，都可以使用这个方法。
 * 比如show databases,show tables等。
 *
 * @author axeon
 */
public class StringArrayListTask extends LocalTaskAdapter<ArrayList<String[]>> {

    public StringArrayListTask(long mysqlClusterId, LocalCmdCallback<ArrayList<String[]>> localCmdCallback) {
        super(mysqlClusterId, localCmdCallback);
        this.data = new ArrayList<>();
    }


    /**
     * 收到ResultSetHeader数据包。
     *
     * @param buf
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        ResultSetHeaderPacket resultSetHeaderPacket = new ResultSetHeaderPacket();
        resultSetHeaderPacket.readPayLoad(buf);
        fieldCount = resultSetHeaderPacket.fieldCount;
    }

    /**
     * 收到RowDataPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        RowDataPacket rowDataPacket = new RowDataPacket(fieldCount);
        rowDataPacket.readPayLoad(buf);
        String[] strings = new String[rowDataPacket.fieldCount];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = new String(rowDataPacket.fieldValues.get(i));
        }
        data.add(strings);
    }

    /**
     * 收到Error数据包。
     *
     * @param buf
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.readPayLoad(buf);
        errorNo = errorPacket.errorNo;
        errorMessage = errorPacket.message;
    }

}
