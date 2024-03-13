package uw.mydb.constant;

/**
 * 全局常量配置。
 *
 * @author axeon
 */
public class GlobalConstants {

    /**
     * 协议版本
     **/
    public static final byte PROTOCOL_VERSION = 10;

    /**
     * mydb服务器版本。
     * 第一句一定要是mysql的版本号，否则很多客户端无法连接。
     **/
    public static final String SERVER_VERSION = "8.0.16-uw-mydb-1.0-20200608";

    public static final String SINGLE_NODE_HEARTBEAT_SQL = "select 1";

    public static final String MASTER_SLAVE_HEARTBEAT_SQL = "show slave status";

    public static final String GROUP_REPLICATION_HEARTBEAT_SQL = "show slave status";

    public static final String[] MYSQL_SLAVE_STATUS_COLUMNS = {
            "Seconds_Behind_Master",
            "Slave_IO_Running",
            "Slave_SQL_Running",
            "Slave_IO_State",
            "Master_Host",
            "Master_User",
            "Master_Port",
            "Connect_Retry",
            "Last_IO_Error"};

    public static final String[] MYSQL_CLUSTER_STATUS_COLUMNS = {"Variable_name", "Value"};

    /**
     * 默认的重试次数
     */
    public static final int MAX_RETRY_COUNT = 5;
}
