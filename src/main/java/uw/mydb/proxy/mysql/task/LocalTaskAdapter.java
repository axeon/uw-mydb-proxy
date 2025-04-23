package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.mysql.MySqlSession;
import uw.mydb.proxy.mysql.MySqlSessionCallback;

/**
 * 本地任务Adapter。
 *
 * @param <T>
 * @author axeon
 */
public abstract class LocalTaskAdapter<T> implements MySqlSessionCallback {

    private static final Logger logger = LoggerFactory.getLogger( LocalTaskAdapter.class );

    /**
     * 用于执行的命令的mysqlGroupName。
     */
    protected long mysqlClusterId;

    /**
     * 本地命令回调。
     */
    protected LocalCmdCallback<T> localCmdCallback;

    /**
     * 要执行的sql指令。
     */
    protected String sql;

    /**
     * 实际数据。
     */
    protected T data;

    /**
     * 列数量。
     */
    protected int fieldCount;

    /**
     * 错误信息。
     */
    protected String errorMessage;

    /**
     * 错误编号
     */
    protected int errorNo;

    /**
     * 是否需要运行master上，一般都是写指令。
     */
    protected boolean isMaster;


    public LocalTaskAdapter(long mysqlClusterId, LocalCmdCallback<T> localCmdCallback) {
        this.mysqlClusterId = mysqlClusterId;
        this.localCmdCallback = localCmdCallback;
    }

    /**
     * 执行sql。
     */
    public void run(String sql) {
        this.sql = sql;
        MySqlSession mysqlSession = MySqlClient.getMySqlSession( mysqlClusterId,true );
        if (mysqlSession == null) {
            logger.warn( "无法找到合适的mysqlSession!" );
            return;
        }
        mysqlSession.addCommand(this , sql );
    }

    /**
     * 收到Ok数据包。
     *
     * @param buf
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到Error数据包。
     *
     * @param buf
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {

    }

    /**
     * 收到ResultSetHeader数据包。
     *
     * @param buf
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到FieldPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到FieldEOFPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到RowDataPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到RowDataEOFPacket数据包。
     *
     * @param buf
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 错误提示。
     *
     * @param errorNo
     * @param info
     */
    @Override
    public void onMysqlFailMessage(int errorNo, String info) {
        //此处不用实现，unbind的时候实现了。
    }

    /**
     * 解绑的时候激活回调事件。
     */
    @Override
    public void onFinish() {
        if (errorNo == 0) {
            localCmdCallback.onSuccess( data );
        } else {
            localCmdCallback.onFailure( errorNo, errorMessage );
        }
    }
}
