package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.util.ByteBufUtils;

/**
 * MySqlPacket
 *
 * @author axeon
 */
public abstract class MySqlPacket {

    private static final Logger logger = LoggerFactory.getLogger(MySqlPacket.class);

    /**
     * 空的位置数据。
     */
    private static final byte[] NULL_PACKET_LEN = new byte[3];

    /**
     * 承载类型OK
     */
    public static final byte PACKET_OK = 0;

    /**
     * 承载类型ERROR
     */
    public static final byte PACKET_ERROR = (byte) 0xFF;

    /**
     * 承载类型EOF
     */
    public static final byte PACKET_EOF = (byte) 0xFE;

    /**
     * 承载类型AUTH_MORE_DATA
     */
    public static final byte PACKET_AUTH_MORE_DATA = 1;

    /**
     * 承载类型AUTH切换
     */
    public static final byte PACKET_AUTH_SWITCH = (byte)0xFE;

    /**
     * 承载类型QUIT
     */
    public static final byte PACKET_QUIT = 2;

    /**
     * 当前为load data的响应包
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
     * 包长度
     */
    public int packetLength;

    /**
     * 包ID
     */
    public byte packetId = 0;

    /**
     * 写入payLoad.
     *
     * @param buf
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
     * 读取byteBuf内容到packet
     *
     * @param buf
     */
    public void readPayLoad(ByteBuf buf) {
        packetLength = ByteBufUtils.readUB3(buf);
        packetId = buf.readByte();
        this.read(buf);
    }


    /**
     * 把packet内容写入byteBuf。
     *
     * @param buf
     */
    protected abstract void write(ByteBuf buf);

    /**
     * 读取byteBuf内容到packet
     *
     * @param buf
     */
    protected abstract void read(ByteBuf buf);


    /**
     * 给出当前包大小。
     *
     * @return
     */
    public int getPacketLength() {
        return packetLength;
    }

    /**
     * 获取packetId。
     *
     * @return
     */
    public byte getPacketId() {
        return packetId;
    }

    /**
     * 把数据包直接写入ctx。
     *
     * @param ctx
     */
    public void writeToChannel(ChannelHandlerContext ctx) {
        ByteBuf buf = ctx.alloc().buffer();
        writePayLoad(buf);
        ctx.write(buf);
    }

    /**
     * 把数据包直接写入ctx。
     *
     * @param channel
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
