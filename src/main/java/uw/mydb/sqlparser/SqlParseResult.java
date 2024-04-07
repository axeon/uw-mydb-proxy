package uw.mydb.sqlparser;

import org.slf4j.LoggerFactory;
import uw.mydb.protocol.packet.CommandPacket;
import uw.mydb.protocol.packet.MySqlPacket;
import uw.mydb.vo.DataTable;

import java.util.List;

/**
 * SQL解析路由结果。
 *
 * @author axeon
 */
public class SqlParseResult {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( SqlParseResult.class );

    /**
     * 原始的sql语句。
     */
    private String sourceSql;

    /**
     * schema名。
     */
    private String sourceDatabase;

    /**
     * 表名。
     */
    private String sourceTable;

    /**
     * 是否是DML。
     * 默认为true。
     */
    private boolean isDML = true;

    /**
     * 是否主库操作。
     */
    private Boolean isMasterQuery;

    /**
     * 错误编码。
     */
    private int errorCode;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 单sql结果
     */
    private SqlInfo sqlInfo = null;

    /**
     * 多sql结果。
     */
    private List<SqlInfo> sqlInfoList = null;

    public SqlParseResult(String sourceDatabase, String sourceSql) {
        this.sourceDatabase = sourceDatabase;
        this.sourceSql = sourceSql;
    }

    public String getSourceSql() {
        return sourceSql;
    }

    public String getSourceDatabase() {
        return sourceDatabase;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public boolean isDML() {
        return isDML;
    }

    public void setDML(boolean DML) {
        isDML = DML;
    }

    /**
     * 是否有错误。
     *
     * @return
     */
    public boolean hasError() {
        return errorCode != 0;
    }

    /**
     * 设置错误信息。
     *
     * @param errorCode
     * @param errorMessage
     */
    public void setErrorInfo(int errorCode, String errorMessage) {
        if (errorCode > 0) {
            logger.warn( "SQL_PARSE_ERR[{}]: {}", errorCode, errorMessage );
        }
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 获得错误码。
     *
     * @return
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * 获得错误信息。
     *
     * @return
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 是否master查询。
     *
     * @return
     */
    public boolean isMasterQuery() {
        return isMasterQuery;
    }

    /**
     * 设置master路由状态。
     *
     * @param master
     */
    public void setMasterQuery(boolean master) {
        isMasterQuery = master;
    }

    /**
     * 如果未赋值，则设置master状态
     *
     * @param master
     */
    public void setMasterIfNull(boolean master) {
        if (this.isMasterQuery == null) {
            isMasterQuery = master;
        }
    }

    public SqlInfo getSqlInfo() {
        return sqlInfo;
    }

    public void setSqlInfo(SqlInfo sqlInfo) {
        this.sqlInfo = sqlInfo;
    }

    public List<SqlInfo> getSqlInfoList() {
        return sqlInfoList;
    }

    public void setSqlInfoList(List<SqlInfo> sqlInfoList) {
        this.sqlInfoList = sqlInfoList;
    }

    /**
     * sql信息。
     */
    public static class SqlInfo {

        private DataTable dataTable;

        /**
         * sql拼接builder。
         * 本来只是个中间体，为了减少不必要的转换，作为变量使用。
         */
        private StringBuilder newSqlBuf;

        /**
         * 新的sql。
         */
        private String newSql;

        public SqlInfo(int sqlSize) {
            newSqlBuf = new StringBuilder( sqlSize );
        }

        public SqlInfo(String sql) {
            newSql = sql;
        }

        public long getClusterId() {
            return dataTable.getDataNode().getClusterId();
        }

        public String getDatabase() {
            return dataTable.getDataNode().getDatabase();
        }

        public String getTable() {
            return dataTable.getTable();
        }

        public DataTable getDataTable() {
            return dataTable;
        }

        public void setDataTable(DataTable dataTable) {
            this.dataTable = dataTable;
        }

        public String getNewSql() {
            if (newSql == null) {
                newSql = newSqlBuf.toString();
            }
            return newSql;
        }

        public SqlInfo appendSql(String text) {
            this.newSqlBuf.append( text );
            return this;
        }

        /**
         * 生成packet。
         *
         * @return
         */
        public CommandPacket genPacket() {
            CommandPacket packet = new CommandPacket();
            packet.command = MySqlPacket.CMD_QUERY;
            packet.arg = getNewSql();

            if (logger.isTraceEnabled()) {
                logger.trace( "MySQL执行: {}", packet.arg );
            }
            return packet;
        }
    }

}
