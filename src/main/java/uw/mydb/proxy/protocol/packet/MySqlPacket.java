package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.util.ByteBufUtils;

/**
 * 所有 MySQL 协议包的抽象基类，定义包头（3 字节长度 + 1 字节 packetId）的统一读写逻辑，
 * 并以 {@code public static final byte} 常量形式定义 MySQL 协议的命令字节（COM_xxx）与响应类型（OK/ERROR/EOF 等）。
 * <p>
 * 子类只需实现 {@link #write(ByteBuf)} 与 {@link #read(ByteBuf)} 处理 payload 部分。
 * {@link #writePayLoad(ByteBuf)} 会先写空包头占位、写完 payload 后回退重写真实长度，避免预先计算长度。
 * <p>
 * 非线程安全：每个包实例仅供单次读写使用。
 *
 * @author axeon
 */
public abstract class MySqlPacket {

    private static final Logger logger = LoggerFactory.getLogger(MySqlPacket.class);

    /**
     * 3 字节空长度占位符，{@link #writePayLoad} 先写入再回退重写。
     */
    private static final byte[] NULL_PACKET_LEN = new byte[3];

    /**
     * payload 首字节标识：OK 包。
     */
    public static final byte PACKET_OK = 0;

    /**
     * payload 首字节标识：ERROR 包。
     */
    public static final byte PACKET_ERROR = (byte) 0xFF;

    /**
     * payload 首字节标识：EOF 包（warning_count + status flags，长度 &lt; 9 字节时区分 OK）。
     */
    public static final byte PACKET_EOF = (byte) 0xFE;

    /**
     * payload 首字节标识：认证过程更多数据（caching_sha2_password 快速认证路径）。
     */
    public static final byte PACKET_AUTH_MORE_DATA = 1;

    /**
     * payload 首字节标识：认证插件切换请求。
     */
    public static final byte PACKET_AUTH_SWITCH = (byte)0xFE;

    /**
     * payload 首字节标识：客户端退出（部分场景下使用）。
     */
    public static final byte PACKET_QUIT = 2;

    /**
     * payload 首字节标识：LOAD DATA 本地文件响应里的文件名占位。
     */
    public static final byte LOAD_DATA_PACKET = (byte) 0xfb;

    /**
     * none, this is an internal thread state
     */
    public static final byte CMD_SLEEP = 0;

    // 前端报文类型
    /**
     * mysql_close
     */
    public static final byte CMD_QUIT = 1;
    /**
     * mysql_select_db
     */
    public static final byte CMD_INIT_DB = 2;
    /**
     * mysql_real_query
     */
    public static final byte CMD_QUERY = 3;
    /**
     * mysql_list_fields
     */
    public static final byte CMD_FIELD_LIST = 4;
    /**
     * mysql_create_db (deprecated)
     */
    public static final byte CMD_CREATE_DB = 5;
    /**
     * mysql_drop_db (deprecated)
     */
    public static final byte CMD_DROP_DB = 6;
    /**
     * mysql_refresh
     */
    public static final byte CMD_REFRESH = 7;
    /**
     * mysql_shutdown
     */
    public static final byte CMD_SHUTDOWN = 8;
    /**
     * mysql_stat
     */
    public static final byte CMD_STATISTICS = 9;
    /**
     * mysql_list_processes
     */
    public static final byte CMD_PROCESS_INFO = 10;
    /**
     * none, this is an internal thread state
     */
    public static final byte CMD_CONNECT = 11;
    /**
     * mysql_kill
     */
    public static final byte CMD_PROCESS_KILL = 12;
    /**
     * mysql_dump_debug_info
     */
    public static final byte CMD_DEBUG = 13;
    /**
     * mysql_ping
     */
    public static final byte CMD_PING = 14;
    /**
     * none, this is an internal thread state
     */
    public static final byte CMD_TIME = 15;
    /**
     * none, this is an internal thread state
     */
    public static final byte CMD_DELAYED_INSERT = 16;
    /**
     * mysql_change_user
     */
    public static final byte CMD_CHANGE_USER = 17;
    /**
     * used by slave server mysqlbinlog
     */
    public static final byte CMD_BINLOG_DUMP = 18;
    /**
     * used by slave server to get master table
     */
    public static final byte CMD_TABLE_DUMP = 19;
    /**
     * used by slave to log connection to master
     */
    public static final byte CMD_CONNECT_OUT = 20;
    /**
     * used by slave to register to master
     */
    public static final byte CMD_REGISTER_SLAVE = 21;
    /**
     * mysql_stmt_prepare
     */
    public static final byte CMD_STMT_PREPARE = 22;
    /**
     * mysql_stmt_execute
     */
    public static final byte CMD_STMT_EXECUTE = 23;
    /**
     * mysql_stmt_send_long_data
     */
    public static final byte CMD_STMT_SEND_LONG_DATA = 24;
    /**
     * mysql_stmt_close
     */
    public static final byte CMD_STMT_CLOSE = 25;
    /**
     * mysql_stmt_reset
     */
    public static final byte CMD_STMT_RESET = 26;
    /**
     * mysql_set_server_option
     */
    public static final byte CMD_SET_OPTION = 27;
    /**
     * mysql_stmt_fetch
     */
    public static final byte CMD_STMT_FETCH = 28;
    /**
     * mysql_stmt_fetch
     */
    public static final byte CMD_DAEMON = 29;
    /**
     * mysql_stmt_fetch
     */
    public static final byte CMD_BINLOG_DUMP_GTID = 30;
    /**
     * mysql_stmt_fetch
     */
    public static final byte CMD_RESET_CONNECTION = 31;
    /**
     * Mycat heartbeat
     */
    public static final byte CMD_HEARTBEAT = 64;

    /**
     * MORE RESULTS
     */
    public static final int SERVER_MORE_RESULTS_EXISTS = 8;

    /**
     * 包 payload 长度（不含 4 字节包头）。
     */
    public int packetLength;

    /**
     * MySQL 协议 packetId，请求/响应序列号，初始为 0，每包 +1。
     */
    public byte packetId = 0;

    /**
     * 写入完整包：先写 3 字节占位 + packetId，写完 payload 后回退重写真实长度。
     *
     * @param buf 目标 ByteBuf
     */
    public void writePayLoad(ByteBuf buf) {
        //写入空包头。
        buf.writeBytes(NULL_PACKET_LEN);
        //写入packetId头部。
        buf.writeByte(packetId);
        //记录起始位置。
        int startPos = buf.writerIndex();
        //写入业务数据
        this.write(buf);
        //记录结束位置
        int endPos = buf.writerIndex();
        //指针回到开始位置，重写包头
        buf.writerIndex(startPos - 4);
        //计算包长度。
        packetLength = endPos - startPos;
        ByteBufUtils.writeUB3(buf, packetLength);
        //写完包头再回去。
        buf.writerIndex(endPos);
//        System.out.println(ByteBufUtil.prettyHexDump(buf,0,endPos));
    }


    /**
     * 读取完整包：解析 3 字节长度与 packetId 后，调用 {@link #read} 让子类解析 payload。
     *
     * @param buf 源 ByteBuf（position 在包头起始）
     */
    public void readPayLoad(ByteBuf buf) {
        packetLength = ByteBufUtils.readUB3(buf);
        packetId = buf.readByte();
        this.read(buf);
    }


    /**
     * 子类实现：把 payload 字段写入 ByteBuf（不含包头）。
     *
     * @param buf 目标 ByteBuf
     */
    protected abstract void write(ByteBuf buf);

    /**
     * 子类实现：从 ByteBuf 读取 payload 字段（不含包头）。
     *
     * @param buf 源 ByteBuf（position 在 payload 起始）
     */
    protected abstract void read(ByteBuf buf);


    /**
     * @return payload 长度（不含 4 字节包头）
     */
    public int getPacketLength() {
        return packetLength;
    }

    /**
     * @return MySQL 协议 packetId
     */
    public byte getPacketId() {
        return packetId;
    }

    /**
     * 把当前包写入 channel 的 outbound 队列（分配新 ByteBuf，{@link #writePayLoad} 后 {@code ctx.write}）。
     *
     * @param ctx channel 上下文
     */
    public void writeToChannel(ChannelHandlerContext ctx) {
        ByteBuf buf = ctx.alloc().buffer();
        writePayLoad(buf);
        ctx.write(buf);
    }

    /**
     * 把当前包写入 channel 的 outbound 队列，使用 channel 的 allocator 分配 ByteBuf。
     *
     * @param channel 目标 channel
     */
    public void writeToChannel(Channel channel) {
        ByteBuf buf = channel.alloc().buffer();
        writePayLoad(buf);
        channel.write(buf);
    }

    @Override
    public String toString() {
        return new StringBuilder().append(this.getClass().getSimpleName()).append("{length=").append(packetLength).append(",id=")
                .append(packetId).append('}').toString();
    }

}
