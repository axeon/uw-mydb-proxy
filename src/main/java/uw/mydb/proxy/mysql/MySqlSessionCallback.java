package uw.mydb.proxy.mysql;

import io.netty.buffer.ByteBuf;

/**
 * session回调接口。
 *
 * @author axeon
 */
public interface MySqlSessionCallback {

    /**
     * 获取客户端信息。
     * @return
     */
    String getClientInfo();

    /**
     * 收到Ok数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveOkPacket(byte packetId, ByteBuf buf);

    /**
     * 收到Error数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveErrorPacket(byte packetId, ByteBuf buf);

    /**
     * 收到ResultSetHeader数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf);

    /**
     * 收到FieldPacket数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveFieldDataPacket(byte packetId, ByteBuf buf);

    /**
     * 收到FieldEOFPacket数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf);

    /**
     * 收到RowDataPacket数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveRowDataPacket(byte packetId, ByteBuf buf);

    /**
     * 收到RowDataEOFPacket数据包。
     *
     * @param packetId
     * @param buf
     */
    void receiveRowDataEOFPacket(byte packetId, ByteBuf buf);

    /**
     * 发送错误消息。
     *
     * @param errorNo
     * @param info
     */
    void onMysqlFailMessage(int errorNo, String info);

    /**
     * 通知解绑定。
     */
    void onFinish();

}
