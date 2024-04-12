package uw.mydb.sqlparse;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
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
     * 原始表。
     * 当前只存储主表。
     */
    protected String sourceTable;

    /**
     * 原始的sql语句。
     */
    protected String sourceSql;

    /**
     * schema名。
     */
    protected String sourceDatabase;

    /**
     * 是否是DML。
     * 默认为true。
     */
    protected boolean isDML = true;

    /**
     * 是否主库操作。
     */
    protected boolean isMasterQuery = true;

    /**
     * sql类型。
     */
    protected int sqlType;

    /**
     * 错误编码。
     */
    protected int errorCode;

    /**
     * 错误信息。
     */
    protected String errorMessage;

    /**
     * 单一sqlInfo。
     */
    protected SqlInfo sqlInfo;

    /**
     * sqlInfo列表。
     */
    protected List<SqlInfo> sqlInfoList;

    public SqlParseResult(String sourceDatabase, String sourceSql) {
        this.sourceDatabase = sourceDatabase;
        this.sourceSql = sourceSql;
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this ,ToStringStyle.MULTI_LINE_STYLE)
                .append( "sourceTable", sourceTable )
                .append( "sourceSql", sourceSql )
                .append( "sourceDatabase", sourceDatabase )
                .append( "isDML", isDML )
                .append( "isMasterQuery", isMasterQuery )
                .append( "sqlType", sqlType )
                .append( "errorCode", errorCode )
                .append( "errorMessage", errorMessage )
                .append( "sqlInfo", sqlInfo )
                .append( "sqlInfoList", sqlInfoList )
                .toString();
    }

    public int getSqlType() {
        return sqlType;
    }

    public void setSqlType(int sqlType) {
        this.sqlType = sqlType;
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
         * sql信息。
         */
        private StringBuilder newSqlBuf;

        /**
         * 新的sql。
         */
        private String newSql;

        public SqlInfo(int sqlSize) {
            newSqlBuf = new StringBuilder( sqlSize );
        }

        public SqlInfo(DataTable dataTable, String newSql) {
            this.dataTable = dataTable;
            this.newSql = newSql;
        }

        @Override
        public String toString() {
            return new ToStringBuilder( this, ToStringStyle.MULTI_LINE_STYLE )
                    .append( "dataTable", dataTable )
                    .append( "newSql", getNewSql() )
                    .toString();
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
                newSqlBuf = null;
            }
            return newSql;
        }

        public SqlInfo appendSql(String text) {
            this.newSqlBuf.append( text );
            return this;
        }


    }


}
