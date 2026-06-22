package uw.mydb.proxy.mysql.task;

/**
 * 服务器内部直接操作 MySQL 数据库的任务回调接口。
 *
 * <p>由本地任务（{@code LocalTaskAdapter} 及其子类）持有，在 session 命令结束后由
 * {@link LocalTaskAdapter#onFinish()} 触发：成功调用 {@link #onSuccess(Object)}，
 * 失败调用 {@link #onFailure(int, String)}。
 *
 * @param <T> 任务返回数据类型
 * @author axeon
 */
public interface LocalCmdCallback<T> {

    /**
     * 命令执行成功时回调。
     *
     * @param data 解析后的结果数据（类型由具体任务决定，如 Long 受影响行数、List 行集合）
     */
    void onSuccess(T data);

    /**
     * 命令执行失败时回调。
     *
     * @param errorNo MySQL 错误号
     * @param message 错误文本
     */
    void onFailure(int errorNo, String message);

}
