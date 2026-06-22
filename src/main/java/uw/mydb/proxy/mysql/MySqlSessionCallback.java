package uw.mydb.proxy.mysql;

import io.netty.buffer.ByteBuf;

/**
 * MySQL session 回调接口，由前端任务（如 {@code LocalTaskAdapter}）实现，
 * 接收 {@link MySqlSession} 在协议处理过程中解析出的各类数据包事件。
 *
 * <p>所有方法均在 session channel 归属的 EventLoop 线程上被调用，实现方不应在其中执行阻塞操作。
 * 一个 session 同一时刻只绑定一个 callback 实例。
 *
 * @author axeon
 */
public interface MySqlSessionCallback {

    /**
     * @return 客户端标识信息（用于 SQL 统计埋点），通常为任务类名或前端连接标识
     */
    String getClientInfo();

    /**
     * 收到 OkPacket（非结果集场景，如 INSERT/UPDATE/DELETE 完成或结果集结束后的 OK）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包（含包头，readerIndex 在 status 字节之前）
     */
    void receiveOkPacket(byte packetId, ByteBuf buf);

    /**
     * 收到 ErrorPacket（命令执行失败）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveErrorPacket(byte packetId, ByteBuf buf);

    /**
     * 收到 ResultSetHeaderPacket（结果集开头，含 fieldCount 列数）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf);

    /**
     * 收到结果集列定义包（FieldPacket）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveFieldDataPacket(byte packetId, ByteBuf buf);

    /**
     * 收到结果集列定义区结束的 EOF 包。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf);

    /**
     * 收到结果集数据行包（RowDataPacket）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveRowDataPacket(byte packetId, ByteBuf buf);

    /**
     * 收到结果集数据区结束的 EOF 包（或新版 OK 包表示结果集结束）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    void receiveRowDataEOFPacket(byte packetId, ByteBuf buf);

    /**
     * MySQL 连接/命令执行发生错误时由 {@link MySqlSession#failMessage} 回调，用于通知前端失败信息。
     *
     * @param errorNo MySQL 错误号
     * @param info    错误文本
     */
    void onMysqlFailMessage(int errorNo, String info);

    /**
     * 命令结束、callback 被解绑时由 {@link MySqlSession#unbindCallback} 回调一次，
     * 实现方通常在此触发最终的业务成功/失败回调。
     */
    void onFinish();

}
