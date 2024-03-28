package uw.mydb.mysql2.task;

import io.netty.buffer.ByteBuf;
import uw.mydb.protocol.packet.ErrorPacket;
import uw.mydb.protocol.packet.OKPacket;

/**
 * 执行数据库操作的任务。
 * 一般对应insert,update,delete，只返回affect rows。
 *
 * @author axeon
 */
public class ExecuteSqlTask extends LocalTaskAdapter<Long> {

    public ExecuteSqlTask(long mysqlClusterId, LocalCmdCallback<Long> localCmdCallback) {
        super(mysqlClusterId, localCmdCallback);
        //写指令，在这里标识出来
        this.isMaster = true;
    }

    /**
     * 收到Ok数据包。
     *
     * @param buf
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        OKPacket okPacket = new OKPacket();
        okPacket.readPayLoad(buf);
        data = okPacket.affectedRows;
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
