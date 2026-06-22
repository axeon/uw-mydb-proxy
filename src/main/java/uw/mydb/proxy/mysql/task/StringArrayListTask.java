package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.ResultSetHeaderPacket;
import uw.mydb.proxy.protocol.packet.ResultSetRowDataPacket;

import java.util.ArrayList;

/**
 * 返回多列字符串数组的任务，每行结果转为 {@code String[]} 收集。
 *
 * <p>典型场景：需要返回完整多列行数据（每行为 String[]，元素顺序与 fieldCount 对应）的查询，
 * 如 {@code select a,b,c from ...}。命令结束后 {@link #data} 为 {@code ArrayList<String[]>}。
 *
 * @author axeon
 */
public class StringArrayListTask extends LocalTaskAdapter<ArrayList<String[]>> {

    /**
     * @param mysqlClusterId   目标 MySQL cluster id
     * @param localCmdCallback 业务回调（成功时返回多列字符串数组的列表）
     */
    public StringArrayListTask(long mysqlClusterId, LocalCmdCallback<ArrayList<String[]>> localCmdCallback) {
        super(mysqlClusterId, localCmdCallback);
        this.data = new ArrayList<>();
    }




    /**
     * 解析 ResultSetHeaderPacket，记录列数 fieldCount 供后续 row 解析使用。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
        ResultSetHeaderPacket resultSetHeaderPacket = new ResultSetHeaderPacket();
        resultSetHeaderPacket.readPayLoad(buf);
        fieldCount = resultSetHeaderPacket.fieldCount;
    }

    /**
     * 解析数据行包，把所有列转成 {@code String[]} 加入 {@link #data}。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        ResultSetRowDataPacket rowDataPacket = new ResultSetRowDataPacket(fieldCount);
        rowDataPacket.readPayLoad(buf);
        String[] strings = new String[rowDataPacket.fieldCount];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = new String(rowDataPacket.fieldValues.get(i));
        }
        data.add(strings);
    }

    /**
     * @return 任务类名，用于 SQL 统计埋点
     */
    @Override
    public String getClientInfo() {
        return this.getClass().getSimpleName();
    }

    /**
     * 解析 ErrorPacket，记录错误号与错误文本。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.readPayLoad(buf);
        errorNo = errorPacket.errorNo;
        errorMessage = errorPacket.message;
    }

}
