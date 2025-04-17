package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.OkPacket;

/**
 * 执行数据库操作的任务。
 * 一般对应insert,update,delete，只返回affect rows。
 *
 * @author axeon
 */
public class ExecuteSqlTask extends LocalTaskAdapter<Long> {

    public ExecuteSqlTask(long mysqlClusterId, LocalCmdCallback<Long> localCmdCallback) {
        super( mysqlClusterId, localCmdCallback );
        //写指令，在这里标识出来
        this.isMaster = true;
    }

    /**
     * 获取客户端信息。
     *
     * @return
     */
    @Override
    public String getClientInfo() {
        return this.getClass().getSimpleName();
    }

    /**
     * 收到Ok数据包。
     *
     * @param buf
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        OkPacket okPacket = new OkPacket();
        okPacket.readPayLoad( buf );
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
        errorPacket.readPayLoad( buf );
        errorNo = errorPacket.errorNo;
        errorMessage = errorPacket.message;
    }

}
