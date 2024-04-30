package uw.mydb.proxy.protocol.constant;

/**
 * Capabilities标识定义
 *
 * @author axeon
 */
public class MySQLCapability {

    public static final int SERVER_STATUS_IN_TRANS = 1;
    public static final int SERVER_STATUS_AUTOCOMMIT = 2; // Server in auto_commit mode
    public static final int SERVER_MORE_RESULTS_EXISTS = 8; // Multi query - next query exists
    public static final int SERVER_QUERY_NO_GOOD_INDEX_USED = 16;
    public static final int SERVER_QUERY_NO_INDEX_USED = 32;
    public static final int SERVER_STATUS_CURSOR_EXISTS = 64;
    public static final int SERVER_STATUS_LAST_ROW_SENT = 128; // The server status for 'last-row-sent'
    public static final int SERVER_QUERY_WAS_SLOW = 2048;
    public static final int SERVER_SESSION_STATE_CHANGED = 1 << 14; // 16384

    /**
     * Use the improved version of Old Password Authentication.
     * <p>
     * Not used.
     * <p>
     * Note
     * Assumed to be set since 4.1.1.
     */
    public static final int CLIENT_LONG_PASSWORD = 1;

    /**
     * Send found rows instead of affected rows in EOF_Packet.
     */
    public static final int CLIENT_FOUND_ROWS = 2;

    /**
     * Get all column flags.
     * <p>
     * Longer flags in Protocol::ColumnDefinition320.
     * <p>
     * Server
     * Supports longer flags.
     * <p>
     * Client
     * Expects longer flags.
     */
    public static final int CLIENT_LONG_FLAG = 4;

    /**
     * Database (schema) name can be specified on connect in Handshake Response Packet.
     * <p>
     * Server
     * Supports schema-name in Handshake Response Packet.
     * <p>
     * Client
     * Handshake Response Packet contains a schema-name.
     * <p>
     * See also
     * send_client_reply_packet()
     */
    public static final int CLIENT_CONNECT_WITH_DB = 8;

    /**
     * @deprecated DEPRECATED: Don't allow database.table.column.
     */
    public static final int CLIENT_NO_SCHEMA = 16;

    /**
     * Compression protocol supported.
     * <p>
     * Server
     * Supports compression.
     * <p>
     * Client
     * Switches to Compression compressed protocol after successful authentication.
     */
    public static final int CLIENT_COMPRESS = 32;

    /**
     * Special handling of ODBC behavior.
     * <p>
     * Note
     * No special behavior since 3.22.
     */
    public static final int CLIENT_ODBC = 64;

    /**
     * Can use LOAD DATA LOCAL.
     * <p>
     * Server
     * Enables the LOCAL INFILE request of LOAD DATA|XML.
     * <p>
     * Client
     * Will handle LOCAL INFILE request.
     */
    public static final int CLIENT_LOCAL_FILES = 128;

    /**
     * Ignore spaces before '('.
     * <p>
     * Server
     * Parser can ignore spaces before '('.
     * <p>
     * Client
     * Let the parser ignore spaces before '('.
     */
    public static final int CLIENT_IGNORE_SPACE = 256;

    /**
     * Enable/disable multi-results.
     * <p>
     * Server
     * Can send multiple resultsets for COM_QUERY. Error if the server needs to send them and client does not support them.
     * <p>
     * Client
     * Can handle multiple resultsets for COM_QUERY.
     * <p>
     * Requires
     * CLIENT_PROTOCOL_41
     * <p>
     * See also
     * mysql_execute_command(), sp_head::MULTI_RESULTS
     */
    public static final int CLIENT_PROTOCOL_41 = 512;

    /**
     * This is an interactive client.
     * <p>
     * Use System_variables::net_wait_timeout versus System_variables::net_interactive_timeout.
     * <p>
     * Server
     * Supports interactive and noninteractive clients.
     * <p>
     * Client
     * Client is interactive.
     * <p>
     * See also
     * mysql_real_connect()
     */
    public static final int CLIENT_INTERACTIVE = 1 << 10;

    /**
     * Use SSL encryption for the session.
     * <p>
     * Server
     * Supports SSL
     * <p>
     * Client
     * Switch to SSL after sending the capability-flags.
     */
    public static final int CLIENT_SSL = 1 << 11;

    /**
     * Client only flag.
     * <p>
     * Not used.
     * <p>
     * Client
     * Do not issue SIGPIPE if network failures occur (libmysqlclient only).
     * <p>
     * See also
     * mysql_real_connect()
     */
    public static final int CLIENT_IGNORE_SIGPIPE = 1 << 12;

    /**
     * Client knows about transactions.
     * <p>
     * Server
     * Can send status flags in OK_Packet / EOF_Packet.
     * <p>
     * Client
     * Expects status flags in OK_Packet / EOF_Packet.
     * <p>
     * Note
     * This flag is optional in 3.23, but always set by the server since 4.0.
     * See also
     * send_server_handshake_packet(), parse_client_handshake_packet(), net_send_ok(), net_send_eof()
     */
    public static final int CLIENT_TRANSACTIONS = 1 << 13;

    /**
     * @deprecated DEPRECATED: Old flag for 4.1 protocol
     */
    public static final int CLIENT_RESERVED = 1 << 14;

    /**
     * @deprecated DEPRECATED: Old flag for 4.1 authentication \ CLIENT_SECURE_CONNECTION.
     */
    public static final int CLIENT_SECURE_CONNECTION = 1 << 15;

    /**
     * Enable/disable multi-stmt support.
     * <p>
     * Also sets CLIENT_MULTI_RESULTS. Currently not checked anywhere.
     * <p>
     * Server
     * Can handle multiple statements per COM_QUERY and COM_STMT_PREPARE.
     * <p>
     * Client
     * May send multiple statements per COM_QUERY and COM_STMT_PREPARE.
     * <p>
     * Note
     * Was named CLIENT_MULTI_QUERIES in 4.1.0, renamed later.
     * Requires
     * CLIENT_PROTOCOL_41
     */
    public static final int CLIENT_MULTI_STATEMENTS = 1 << 16;


    /**
     * Enable/disable multi-results.
     * <p>
     * Server
     * Can send multiple resultsets for COM_QUERY. Error if the server needs to send them and client does not support them.
     * <p>
     * Client
     * Can handle multiple resultsets for COM_QUERY.
     * <p>
     * Requires
     * CLIENT_PROTOCOL_41
     * <p>
     * See also
     * mysql_execute_command(), sp_head::MULTI_RESULTS
     */
    public static final int CLIENT_MULTI_RESULTS = 1 << 17;

    /**
     * ServerCan send multiple resultsets for COM_STMT_EXECUTE.
     * Client
     * Can handle multiple resultsets for COM_STMT_EXECUTE.
     * Value
     * 0x00040000
     * Requires
     * CLIENT_PROTOCOL_41
     */
    public static final int CLIENT_PS_MULTI_RESULTS = 1 << 18;

    /**
     * Client supports plugin authentication.
     * <p>
     * Server
     * Sends extra data in Initial Handshake Packet and supports the pluggable authentication protocol.
     * <p>
     * Client
     * Supports authentication plugins.
     * <p>
     * Requires
     * CLIENT_PROTOCOL_41
     * <p>
     * See also
     * send_change_user_packet(), send_client_reply_packet(), run_plugin_auth(), parse_com_change_user_packet(), parse_client_handshake_packet()
     */
    public static final int CLIENT_PLUGIN_AUTH = 1 << 19;

    /**
     * Client supports connection attributes.
     * <p>
     * Server
     * Permits connection attributes in Protocol::HandshakeResponse41.
     * <p>
     * Client
     * Sends connection attributes in Protocol::HandshakeResponse41.
     * <p>
     * See also
     * send_client_connect_attrs(), read_client_connect_attrs()
     */
    public static final int CLIENT_CONNECT_ATTRS = 1 << 20;

    /**
     * Enable authentication response packet to be larger than 255 bytes.
     * <p>
     * When the ability to change default plugin require that the initial password field in the Protocol::HandshakeResponse41 paclet can be of arbitrary size. However, the 4.1
     * client-server protocol limits the length of the auth-data-field sent from client to server to 255 bytes. The solution is to change the type of the field to a true length
     * encoded string and indicate the protocol change with this client capability flag.
     * <p>
     * Server
     * Understands length-encoded integer for auth response data in Protocol::HandshakeResponse41.
     * <p>
     * Client
     * Length of auth response data in Protocol::HandshakeResponse41 is a length-encoded integer.
     * <p>
     * Note
     * The flag was introduced in 5.6.6, but had the wrong value.
     * See also
     * send_client_reply_packet(), parse_client_handshake_packet(), get_56_lenc_string(), get_41_lenc_string()
     */
    public static final int CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 1 << 21;

    /**
     * Don't close the connection for a user account with expired password.
     * <p>
     * Server
     * Announces support for expired password extension.
     * <p>
     * Client
     * Can handle expired passwords.
     * <p>
     * See also
     * MYSQL_OPT_CAN_HANDLE_EXPIRED_PASSWORDS, disconnect_on_expired_password ACL_USER::password_expired, check_password_lifetime(), acl_authenticate()
     */
    public static final int CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS = 1 << 22;

    /**
     * Capable of handling server state change information.
     * <p>
     * Its a hint to the server to include the state change information in OK_Packet.
     * <p>
     * Server
     * Can set SERVER_SESSION_STATE_CHANGED in the SERVER_STATUS_flags_enum and send Session State Information in a OK_Packet.
     * <p>
     * Client
     * Expects the server to send Session State Information in a OK_Packet.
     * <p>
     * See also
     * enum_session_state_type, read_ok_ex(), net_send_ok(), Session_tracker, State_tracker
     */
    public static final int CLIENT_SESSION_TRACK = 1 << 23;

    /**
     * Client no longer needs EOF_Packet and will use OK_Packet instead.
     * <p>
     * See also
     * net_send_ok()
     * Server
     * Can send OK after a Text Resultset.
     * <p>
     * Client
     * Expects an OK_Packet (instead of EOF_Packet) after the resultset rows of a Text Resultset.
     * <p>
     * Background
     * To support CLIENT_SESSION_TRACK, additional information must be sent after all successful commands. Although the OK_Packet is extensible, the EOF_Packet is not due to the
     * overlap of its bytes with the content of the Text Resultset Row.
     * <p>
     * Therefore, the EOF_Packet in the Text Resultset is replaced with an OK_Packet. EOF_Packet is deprecated as of MySQL 5.7.5.
     * <p>
     * See also
     * cli_safe_read_with_ok(), read_ok_ex(), net_send_ok(), net_send_eof()
     */
    public static final int CLIENT_DEPRECATE_EOF = 1 << 24;

    /**
     * The client can handle optional metadata information in the resultset.
     */
    public static final int CLIENT_OPTIONAL_RESULTSET_METADATA = 1 << 25;

    /**
     * Compression protocol extended to support zstd compression method.
     */
    public static final int CLIENT_ZSTD_COMPRESSION_ALGORITHM = 1 << 26;

    /**
     * Support optional extension for query parameters into the COM_QUERY and COM_STMT_EXECUTE packets.
     */
    public static final int CLIENT_QUERY_ATTRIBUTES = 1 << 27;

    /**
     * Support Multi-factor authentication.
     */
    public static final int MULTI_FACTOR_AUTHENTICATION = 1 << 28;

    /**
     * This flag will be reserved to extend the 32bit capabilities structure to 64bits.
     */
    public static final int CLIENT_CAPABILITY_EXTENSION = 1 << 29;

    /**
     * Verify server certificate.
     */
    public static final int CLIENT_SSL_VERIFY_SERVER_CERT = 1 << 30;

    /**
     * Don't reset the options after an unsuccessful connect.
     */
    public static final int CLIENT_REMEMBER_OPTIONS = 1 << 31;


    public static long initClientFlags() {
        long flag = 0;
        flag |= MySQLCapability.CLIENT_LONG_PASSWORD;
        flag |= MySQLCapability.CLIENT_FOUND_ROWS;
        flag |= MySQLCapability.CLIENT_LONG_FLAG;
        flag |= MySQLCapability.CLIENT_CONNECT_WITH_DB;
//        flag |= MySQLCapability.CLIENT_NO_SCHEMA;
//        flag |= MySQLCapability.CLIENT_COMPRESS;
//        flag |= MySQLCapability.CLIENT_ODBC;
        flag |= MySQLCapability.CLIENT_LOCAL_FILES;
        flag |= MySQLCapability.CLIENT_IGNORE_SPACE;
        flag |= MySQLCapability.CLIENT_PROTOCOL_41;
        flag |= MySQLCapability.CLIENT_INTERACTIVE;
//        flag |= MySQLCapability.CLIENT_SSL;
        flag |= MySQLCapability.CLIENT_IGNORE_SIGPIPE;
        flag |= MySQLCapability.CLIENT_TRANSACTIONS;
//        flag |= MySQLCapability.CLIENT_RESERVED;
//        flag |= MySQLCapability.CLIENT_SECURE_CONNECTION;
        flag |= MySQLCapability.CLIENT_MULTI_STATEMENTS;
        flag |= MySQLCapability.CLIENT_MULTI_RESULTS;
        flag |= MySQLCapability.CLIENT_PS_MULTI_RESULTS;
        flag |= MySQLCapability.CLIENT_PLUGIN_AUTH;
//        flag |= MySQLCapability.CLIENT_CONNECT_ATTRS ;
        flag |= MySQLCapability.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA;
//        flag |= MySQLCapability.CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS;
        flag |= MySQLCapability.CLIENT_SESSION_TRACK;
        flag |= MySQLCapability.CLIENT_DEPRECATE_EOF;
//        flag |= MySQLCapability.CLIENT_OPTIONAL_RESULTSET_METADATA;
//        flag |= MySQLCapability.CLIENT_ZSTD_COMPRESSION_ALGORITHM;
//        flag |= MySQLCapability.CLIENT_QUERY_ATTRIBUTES;
//        flag |= MySQLCapability.MULTI_FACTOR_AUTHENTICATION;

        return flag;
    }

    public static long getServerCapabilities() {
        long flag = 0;
        flag |= MySQLCapability.CLIENT_LONG_PASSWORD;
        flag |= MySQLCapability.CLIENT_FOUND_ROWS;
        flag |= MySQLCapability.CLIENT_LONG_FLAG;
        flag |= MySQLCapability.CLIENT_CONNECT_WITH_DB;
//        flag |= MySQLCapability.CLIENT_NO_SCHEMA;
//        flag |= MySQLCapability.CLIENT_COMPRESS;
//        flag |= MySQLCapability.CLIENT_ODBC;
        flag |= MySQLCapability.CLIENT_LOCAL_FILES;
        flag |= MySQLCapability.CLIENT_IGNORE_SPACE;
        flag |= MySQLCapability.CLIENT_PROTOCOL_41;
        flag |= MySQLCapability.CLIENT_INTERACTIVE;
//        flag |= MySQLCapability.CLIENT_SSL;
        flag |= MySQLCapability.CLIENT_IGNORE_SIGPIPE;
        flag |= MySQLCapability.CLIENT_TRANSACTIONS;
//        flag |= MySQLCapability.CLIENT_RESERVED;
        flag |= MySQLCapability.CLIENT_SECURE_CONNECTION;
        flag |= MySQLCapability.CLIENT_MULTI_STATEMENTS;
        flag |= MySQLCapability.CLIENT_MULTI_RESULTS;
        flag |= MySQLCapability.CLIENT_PS_MULTI_RESULTS;
        flag |= MySQLCapability.CLIENT_PLUGIN_AUTH;
//        flag |= MySQLCapability.CLIENT_CONNECT_ATTRS ;
        flag |= MySQLCapability.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA;
//        flag |= MySQLCapability.CLIENT_CAN_HANDLE_EXPIRED_PASSWORDS;
        flag |= MySQLCapability.CLIENT_SESSION_TRACK;
        flag |= MySQLCapability.CLIENT_DEPRECATE_EOF;
//        flag |= MySQLCapability.CLIENT_OPTIONAL_RESULTSET_METADATA;
//        flag |= MySQLCapability.CLIENT_ZSTD_COMPRESSION_ALGORITHM;
//        flag |= MySQLCapability.CLIENT_QUERY_ATTRIBUTES;
//        flag |= MySQLCapability.MULTI_FACTOR_AUTHENTICATION;
        return flag;
    }

    public static int getLower2Bytes(long value) {
        return (int) (value & 0x0000ffff);
    }

    public static int getUpper2Bytes(long value) {
        return (int) (value >>> 16);
    }

    public static boolean isClientLongPassword(long value) {
        return (value & CLIENT_LONG_PASSWORD) != 0;
    }

    public static boolean isClientFoundRows(long value) {
        return (value & CLIENT_FOUND_ROWS) != 0;
    }

    public static boolean isClientConnectWithDb(long value) {
        return (value & CLIENT_CONNECT_WITH_DB) != 0;
    }

    public static boolean isClientNoSchema(long value) {
        return (value & CLIENT_NO_SCHEMA) != 0;
    }

    public static boolean isClientCompress(long value) {
        return (value & CLIENT_COMPRESS) != 0;
    }

    public static boolean isClientODBC(long value) {
        return (value & CLIENT_ODBC) != 0;
    }

    public static boolean isClientLocalFiles(long value) {
        return (value & CLIENT_LOCAL_FILES) != 0;
    }

    public static boolean isClientIgnoreSpace(long value) {
        return (value & CLIENT_IGNORE_SPACE) != 0;
    }

    public static boolean isClientProtocol41(long value) {
        return (value & CLIENT_PROTOCOL_41) != 0;
    }

    public static boolean isClientSSL(long value) {
        return (value & CLIENT_SSL) != 0;
    }

    public static boolean isClientIgnoreSigpipe(long value) {
        return (value & CLIENT_IGNORE_SIGPIPE) != 0;
    }

    public static boolean isClientTransactions(long value) {
        return (value & CLIENT_TRANSACTIONS) != 0;
    }

    public static boolean isClientInteractive(long value) {
        return (value & CLIENT_INTERACTIVE) != 0;
    }

    public static boolean isClientReserved(long value) {
        return (value & CLIENT_RESERVED) != 0;
    }

    public static boolean isClientSecureConnection(long value) {
        return (value & CLIENT_SECURE_CONNECTION) != 0;
    }

    public static boolean isClientMultipleStatements(long value) {
        return (value & CLIENT_MULTI_STATEMENTS) != 0;
    }

    public static boolean isClientPSMultipleResults(long value) {
        return (value & CLIENT_PS_MULTI_RESULTS) != 0;
    }

    public static boolean isClientPluginAuth(long value) {
        return (value & CLIENT_PLUGIN_AUTH) != 0;
    }

    public static boolean isClientConnectAttrs(long value) {
        return (value & CLIENT_CONNECT_ATTRS) != 0;
    }

    public static boolean isClientPluginAuthLenencClientData(long value) {
        return (value & CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA) != 0;
    }

    public static boolean isClientSessionTrack(long value) {
        return (value & CLIENT_SESSION_TRACK) != 0;
    }

    public static boolean isClientDeprecateEOF(long value) {
        return (value & CLIENT_DEPRECATE_EOF) != 0;
    }

}
