package uw.mydb.proxy.protocol.packet;

import io.netty.buffer.ByteBuf;
import uw.mydb.proxy.util.ByteBufUtils;

/**
 * From client to server whenever the client wants the server to do something.
 * Bytes         Name
 * -----         ----
 * 1             command
 * n             arg
 * <p>
 * command:      The most common value is 03 CMD_QUERY, because
 * INSERT UPDATE DELETE SELECT etc. have this code.
 * The possible values at time of writing (taken
 * from /include/mysql_com.h for enum_server_command) are:
 * <p>
 * #      Name                Associated client function
 * -      ----                --------------------------
 * 0x00   COM_SLEEP           (none, this is an internal thread state)
 * 0x01   CMD_QUIT            mysql_close
 * 0x02   CMD_INIT_DB         mysql_select_db
 * 0x03   CMD_QUERY           mysql_real_query
 * 0x04   COM_FIELD_LIST      mysql_list_fields
 * 0x05   COM_CREATE_DB       mysql_create_db (deprecated)
 * 0x06   COM_DROP_DB         mysql_drop_db (deprecated)
 * 0x07   COM_REFRESH         mysql_refresh
 * 0x08   COM_SHUTDOWN        mysql_shutdown
 * 0x09   COM_STATISTICS      mysql_stat
 * 0x0a   COM_PROCESS_INFO    mysql_list_processes
 * 0x0b   COM_CONNECT         (none, this is an internal thread state)
 * 0x0c   CMD_PROCESS_KILL    mysql_kill
 * 0x0d   COM_DEBUG           mysql_dump_debug_info
 * 0x0e   CMD_PING            mysql_ping
 * 0x0f   COM_TIME            (none, this is an internal thread state)
 * 0x10   COM_DELAYED_INSERT  (none, this is an internal thread state)
 * 0x11   COM_CHANGE_USER     mysql_change_user
 * 0x12   COM_BINLOG_DUMP     sent by the slave IO thread to request a binlog
 * 0x13   COM_TABLE_DUMP      LOAD TABLE ... FROM MASTER (deprecated)
 * 0x14   COM_CONNECT_OUT     (none, this is an internal thread state)
 * 0x15   COM_REGISTER_SLAVE  sent by the slave to register with the master (optional)
 * 0x16   CMD_STMT_PREPARE    mysql_stmt_prepare
 * 0x17   CMD_STMT_EXECUTE    mysql_stmt_execute
 * 0x18   COM_STMT_SEND_LONG_DATA mysql_stmt_send_long_data
 * 0x19   CMD_STMT_CLOSE      mysql_stmt_close
 * 0x1a   COM_STMT_RESET      mysql_stmt_reset
 * 0x1b   COM_SET_OPTION      mysql_set_server_option
 * 0x1c   COM_STMT_FETCH      mysql_stmt_fetch
 * <p>
 * arg:          The text of the command is just the way the user typed it, there is no processing
 * by the client (except removal of the final ';').
 * This field is not a null-terminated string; however,
 * the size can be calculated from the packet size,
 * and the MySQL client appends '\0' when receiving.
 *
 * @author axeon
 */
public class CommandPacket extends MySqlPacket {

    public byte command = MySqlPacket.CMD_QUERY;
    public String arg;


    @Override
    protected void read(ByteBuf buf) {
        command = buf.readByte();
        arg = ByteBufUtils.readStringWithEof(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        buf.writeByte(command);
        buf.writeBytes(arg.getBytes());
    }

}

