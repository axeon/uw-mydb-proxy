package uw.mydb.sqlparser;

import org.slf4j.LoggerFactory;
import uw.mydb.protocol.packet.CommandPacket;
import uw.mydb.protocol.packet.MySqlPacket;
import uw.mydb.route.RouteAlgorithm;
import uw.mydb.vo.DataTable;
import uw.mydb.vo.TableConfig;

/**
 * SQL解析路由结果。
 *
 * @author axeon
 */
public class SqlParseResult{

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( SqlParseResult.class );

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
     * 错误编码。
     */
    protected int errorCode;

    /**
     * 错误信息。
     */
    protected String errorMessage;

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
     * sql信息。
     */
    public static class SqlInfo {

        /**
         * 关联的DataTable
         */
        protected DataTable dataTable;

        /**
         * 新的sql。
         */
        protected String sql;

        public SqlInfo(DataTable dataTable, String sql) {
            this.dataTable = dataTable;
            this.sql = sql;
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

        public String getSql() {

            return sql;
        }

        /**
         * 生成packet。
         *
         * @return
         */
        public CommandPacket genPacket() {
            CommandPacket packet = new CommandPacket();
            packet.command = MySqlPacket.CMD_QUERY;
            packet.arg = getSql();
            if (logger.isTraceEnabled()) {
                logger.trace( "MySQL执行: {}", packet.arg );
            }
            return packet;
        }
    }

    /**
     * 表路由信息。
     */
    public static class TableRouteData {

        /**
         * 表信息。
         */
        protected TableConfig tableConfig;

        /**
         * 表别名。
         */
        protected String tableAliasName;

        /**
         * 路由数据。
         */
        protected RouteAlgorithm.RouteData routeData;

        /**
         * 绑定的路由结果数据。
         */
        protected RouteAlgorithm.RouteResult routeResult;

        public TableRouteData(TableConfig tableConfig) {
            this.tableConfig = tableConfig;
        }

        public TableRouteData(TableConfig tableConfig, String tableAliasName) {
            this.tableConfig = tableConfig;
            this.tableAliasName = tableAliasName;
        }

        public TableRouteData() {
        }

        public TableConfig getTableConfig() {
            return tableConfig;
        }

        public void setTableConfig(TableConfig tableConfig) {
            this.tableConfig = tableConfig;
        }

        public String getTableAliasName() {
            return tableAliasName;
        }

        public void setTableAliasName(String tableAliasName) {
            this.tableAliasName = tableAliasName;
        }

        public RouteAlgorithm.RouteData getRouteData() {
            return routeData;
        }

        public void setRouteData(RouteAlgorithm.RouteData routeData) {
            this.routeData = routeData;
        }

        public RouteAlgorithm.RouteResult getRouteResult() {
            return routeResult;
        }

        public void setRouteResult(RouteAlgorithm.RouteResult routeResult) {
            this.routeResult = routeResult;
        }
    }
}
