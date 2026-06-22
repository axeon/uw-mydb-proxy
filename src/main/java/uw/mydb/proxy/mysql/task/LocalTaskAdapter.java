package uw.mydb.proxy.mysql.task;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.mysql.MySqlSession;
import uw.mydb.proxy.mysql.MySqlSessionCallback;

/**
 * 服务器内部直接操作 MySQL 的本地任务抽象基类，实现 {@link MySqlSessionCallback}。
 *
 * <p>典型用法：业务方继承本类，覆盖关心的数据包回调方法（如 {@link #receiveOkPacket}、
 * {@link #receiveRowDataPacket}），把结果填充到 {@link #data}；调用 {@link #run(String)} 发起命令，
 * session 命令结束后由 {@link #onFinish()} 根据 errorNo 触发 {@link LocalCmdCallback} 的成功或失败回调。
 *
 * <p>线程安全：本类的回调方法均在 session channel 的 EventLoop 上执行；实例本身不共享，
 * 一个任务实例对应一次命令执行。
 *
 * @param <T> 结果数据类型
 * @author axeon
 */
public abstract class LocalTaskAdapter<T> implements MySqlSessionCallback {

    private static final Logger logger = LoggerFactory.getLogger( LocalTaskAdapter.class );

    /**
     * 目标 MySQL cluster id，{@link #run(String)} 时据此从 {@link MySqlClient} 获取 session。
     */
    protected long mysqlClusterId;

    /**
     * 业务回调对象，命令结束时由 {@link #onFinish()} 触发其成功/失败方法。
     */
    protected LocalCmdCallback<T> localCmdCallback;

    /**
     * 待执行的 SQL 文本，{@link #run(String)} 时设置。
     */
    protected String sql;

    /**
     * 命令执行后解析出的结果数据，由子类在各 receive 方法中填充。
     */
    protected T data;

    /**
     * 当前结果集的列数（来自 ResultSetHeaderPacket.fieldCount），解析 row 数据时使用。
     */
    protected int fieldCount;

    /**
     * 错误文本（来自 ErrorPacket.message）。非空时 {@link #onFinish()} 会触发 onFailure。
     */
    protected String errorMessage;

    /**
     * 错误号（来自 ErrorPacket.errorNo）。为 0 表示成功，非 0 表示失败。
     */
    protected int errorNo;

    /**
     * 是否路由到主库。默认 false，子类（如 {@link ExecuteSqlTask}）写操作时置 true。
     * 注意：当前 {@link #run(String)} 实现固定传 isMaster=true。
     */
    protected boolean isMaster;


    /**
     * @param mysqlClusterId   目标 MySQL cluster id
     * @param localCmdCallback 业务回调
     */
    public LocalTaskAdapter(long mysqlClusterId, LocalCmdCallback<T> localCmdCallback) {
        this.mysqlClusterId = mysqlClusterId;
        this.localCmdCallback = localCmdCallback;
    }

    /**
     * 执行一条 SQL：缓存 SQL 文本，从 {@link MySqlClient} 获取 session 并把本对象作为 callback
     * 交给 {@link MySqlSession#addCommand}。若获取 session 失败（cluster 不存在/无可用节点/超时），
     * 仅打印 WARN 并直接返回，不会触发业务回调。
     *
     * @param sql 待执行 SQL
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
     * 收到 OkPacket 的默认实现（空），子类按需覆盖（如 {@link ExecuteSqlTask} 提取 affectedRows）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveOkPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到 ErrorPacket 的默认实现（空），子类按需覆盖以提取错误信息。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveErrorPacket(byte packetId, ByteBuf buf) {

    }

    /**
     * 收到 ResultSetHeaderPacket 的默认实现（空），子类按需覆盖以读取列数。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveResultSetHeaderPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到列定义包的默认实现（空），子类按需覆盖。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveFieldDataPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到列定义区 EOF 包的默认实现（空）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveFieldDataEOFPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到数据行包的默认实现（空），子类按需覆盖以累加行数据。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveRowDataPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * 收到数据区 EOF 包的默认实现（空）。
     *
     * @param packetId MySQL 协议包序号
     * @param buf      原始数据包
     */
    @Override
    public void receiveRowDataEOFPacket(byte packetId, ByteBuf buf) {
    }

    /**
     * session 报告连接/命令失败时的回调，默认空实现——失败信息由 ErrorPacket 路径写入
     * errorNo/errorMessage，{@link #onFinish()} 据此触发 onFailure。
     *
     * @param errorNo MySQL 错误号
     * @param info    错误文本
     */
    @Override
    public void onMysqlFailMessage(int errorNo, String info) {
        //此处不用实现，unbind的时候实现了。
    }

    /**
     * session 解绑时的最终回调：根据 errorNo 决定触发业务成功还是失败回调。
     * errorNo==0 视为成功调用 {@link LocalCmdCallback#onSuccess(Object)}，
     * 否则调用 {@link LocalCmdCallback#onFailure(int, String)}。
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
