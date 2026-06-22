package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.ResultSetHeaderPacket;
import uw.mydb.proxy.protocol.packet.ResultSetRowDataPacket;

import java.util.ArrayList;

/**
 * 返回单列字符串列表的任务，每行结果只取第一列。
 *
 * <p>典型场景：{@code show databases}、{@code show tables} 等仅返回单列的元数据查询。
 * 命令结束后 {@link #data} 为每行第一列字符串组成的 {@code ArrayList<String>}。
 *
 * @author axeon
 */
public class SingleListTask extends LocalTaskAdapter<ArrayList<String>> {

    /**
     * @param mysqlClusterId   目标 MySQL cluster id
     * @param localCmdCallback 业务回调（成功时返回每行第一列字符串列表）
     */
    public SingleListTask(long mysqlClusterId, LocalCmdCallback<ArrayList<String>> localCmdCallback) {
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
     * 解析数据行包，仅取第一列加入 {@link #data}。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
        ResultSetRowDataPacket rowDataPacket = new ResultSetRowDataPacket(fieldCount);
        rowDataPacket.readPayLoad(buf);
        data.add(new String(rowDataPacket.fieldValues.get(0)));
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

    /**
     * @return 任务类名，用于 SQL 统计埋点
     */
    @Override
    public String getClientInfo() {
        return this.getClass().getSimpleName();
    }
}
