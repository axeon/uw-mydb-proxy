package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.protocol.packet.ErrorPacket;
import uw.mydb.proxy.protocol.packet.OkPacket;

/**
 * 执行写操作（INSERT/UPDATE/DELETE 等）的本地任务，只关心受影响行数。
 *
 * <p>构造时强制 {@code isMaster=true}，确保写请求路由到主库。命令完成后从 OkPacket 取出
 * affectedRows 作为 {@link #data}，由 {@link LocalTaskAdapter#onFinish()} 回调给
 * {@link LocalCmdCallback#onSuccess(Object)}。
 *
 * @author axeon
 */
public class ExecuteSqlTask extends LocalTaskAdapter<Long> {

    /**
     * @param mysqlClusterId    目标 MySQL cluster id
     * @param localCmdCallback  业务回调（成功时返回受影响行数）
     */
    public ExecuteSqlTask(long mysqlClusterId, LocalCmdCallback<Long> localCmdCallback) {
        super( mysqlClusterId, localCmdCallback );
        //写指令，在这里标识出来
        this.isMaster = true;
    }

    /**
     * @return 任务类名，用于 SQL 统计埋点
     */
    @Override
    public String getClientInfo() {
        return this.getClass().getSimpleName();
    }

    /**
     * 解析 OkPacket 提取 affectedRows 作为结果数据。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
        OkPacket okPacket = new OkPacket();
        okPacket.readPayLoad( buf );
        data = okPacket.affectedRows;
    }

    /**
     * 解析 ErrorPacket 提取错误号和消息写入 errorMessage/errorNo，
     * {@link LocalTaskAdapter#onFinish()} 会据此回调 onFailure。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {
        ErrorPacket errorPacket = new ErrorPacket();
        errorPacket.readPayLoad( buf );
        errorNo = errorPacket.errorNo;
        errorMessage = errorPacket.message;
    }

}
