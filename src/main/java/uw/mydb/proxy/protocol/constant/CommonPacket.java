package uw.mydb.proxy.protocol.constant;

import uw.mydb.proxy.protocol.packet.CommandPacket;
import uw.mydb.proxy.protocol.packet.MySqlPacket;

/**
 * 常用的Package包。
 *
 * @author axeon
 */
public class CommonPacket {

    public static final CommandPacket AUTOCOMMIT_ON = new CommandPacket();
    public static final CommandPacket AUTOCOMMIT_OFF = new CommandPacket();
    public static final CommandPacket COMMIT = new CommandPacket();
    public static final CommandPacket ROLLBACK = new CommandPacket();

    public static final CommandPacket READ_UNCOMMITTED = new CommandPacket();
    public static final CommandPacket READ_COMMITTED = new CommandPacket();
    public static final CommandPacket REPEATED_READ = new CommandPacket();
    public static final CommandPacket SERIALIZABLE = new CommandPacket();

    static {
        READ_UNCOMMITTED.packetId = 0;
        READ_UNCOMMITTED.command = MySqlPacket.CMD_QUERY;
        READ_UNCOMMITTED.arg = "SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED";
        READ_COMMITTED.packetId = 0;
        READ_COMMITTED.command = MySqlPacket.CMD_QUERY;
        READ_COMMITTED.arg = "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED";
        REPEATED_READ.packetId = 0;
        REPEATED_READ.command = MySqlPacket.CMD_QUERY;
        REPEATED_READ.arg = "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ";
        SERIALIZABLE.packetId = 0;
        SERIALIZABLE.command = MySqlPacket.CMD_QUERY;
        SERIALIZABLE.arg = "SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE";
        AUTOCOMMIT_ON.packetId = 0;
        AUTOCOMMIT_ON.command = MySqlPacket.CMD_QUERY;
        AUTOCOMMIT_ON.arg = "SET autocommit=1";
        AUTOCOMMIT_OFF.packetId = 0;
        AUTOCOMMIT_OFF.command = MySqlPacket.CMD_QUERY;
        AUTOCOMMIT_OFF.arg = "SET autocommit=0";
        COMMIT.packetId = 0;
        COMMIT.command = MySqlPacket.CMD_QUERY;
        COMMIT.arg = "commit";
        ROLLBACK.packetId = 0;
        ROLLBACK.command = MySqlPacket.CMD_QUERY;
        ROLLBACK.arg = "rollback";
    }
}
