package uw.mydb.proxy.sqlparse;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataTable;

import java.util.List;

/**
 * SQL 解析与路由结果。
 * <p>
 * 由 {@link SqlParser#parse()} 产出，包含：源 SQL/schema/table、SQL 类型（{@link SQLType}）、是否主库查询、
 * 错误信息（errorCode/errorMessage），以及最终生成的可执行 SQL 信息（{@link #sqlInfo} 单节点 或 {@link #sqlInfoList} 多节点，二者互斥）。
 * <p>
 * 线程安全：解析阶段在单一线程内完成，解析完成后对象本身为只读快照，可被后端线程读取。
 *
 * @author axeon
 */
public class SqlParseResult {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger( SqlParseResult.class );

    /**
     * SQL 中解析出的主表名（多表场景下仅记录第一个表），用于路由配置查找与错误 SQL 上报。
     */
    protected String sourceTable;

    /**
     * 原始 SQL 文本（客户端发来的未经改写的 SQL）。
     */
    protected String sourceSql;

    /**
     * 当前会话的默认 database（USE 切换前的 schema）。
     */
    protected String sourceDatabase;

    /**
     * 是否为 DML 语句（INSERT/UPDATE/DELETE/SELECT），默认 true。DDL/管理类 SQL 设为 false。
     */
    protected boolean isDML = true;

    /**
     * 是否强制走主库。默认 true（写操作与显式 hint master）；SELECT 默认 false，可被 hint 覆盖。
     */
    protected boolean isMasterQuery = true;

    /**
     * SQL 类型枚举值（{@link SQLType#getValue()}）。
     */
    protected int sqlType;

    /**
     * 错误编码。0 = 无错误；&lt;0 = 内部信号（如 USE 语句处理后用 -1 阻止 cmdQuery 回包）；&gt;0 = MySQL 错误号。
     */
    protected int errorCode;

    /**
     * 错误信息（errorCode&gt;0 时非空）。
     */
    protected String errorMessage;

    /**
     * 单节点可执行 SQL 信息。sqlInfoList 为空时使用此字段。
     */
    protected SqlInfo sqlInfo;

    /**
     * 多节点可执行 SQL 信息列表（多路由/笛卡尔积场景）。非空时表示多节点执行，由 ProxyMultiNodeHandler 处理。
     */
    protected List<SqlInfo> sqlInfoList;

    /**
     * @param sourceDatabase 当前 schema
     * @param sourceSql      原始 SQL
     */
    public SqlParseResult(String sourceDatabase, String sourceSql) {
        this.sourceDatabase = sourceDatabase;
        this.sourceSql = sourceSql;
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this, ToStringStyle.MULTI_LINE_STYLE )
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

    /**
     * @return SQL 类型枚举值
     */
    public int getSqlType() {
        return sqlType;
    }

    /**
     * @param sqlType SQL 类型枚举值
     */
    public void setSqlType(int sqlType) {
        this.sqlType = sqlType;
    }

    /**
     * @return 原始 SQL 文本
     */
    public String getSourceSql() {
        return sourceSql;
    }

    /**
     * @return 当前 schema
     */
    public String getSourceDatabase() {
        return sourceDatabase;
    }

    /**
     * @return SQL 中解析出的主表名
     */
    public String getSourceTable() {
        return sourceTable;
    }

    /**
     * @param sourceTable 主表名
     */
    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    /**
     * @return 是否 DML
     */
    public boolean isDML() {
        return isDML;
    }

    /**
     * @param DML 是否 DML
     */
    public void setDML(boolean DML) {
        isDML = DML;
    }

    /**
     * 是否有错误（errorCode != 0）。注意 errorCode &lt;0 为内部信号，调用方需进一步区分。
     *
     * @return true 表示存在错误或内部信号
     */
    public boolean hasError() {
        return errorCode != 0;
    }

    /**
     * 设置错误信息。errorCode &gt; 0 时记录 WARN 日志便于排查。
     *
     * @param errorCode    错误编码（&gt;0 为对外 MySQL 错误号；&lt;0 为内部信号）
     * @param errorMessage 错误信息
     */
    public void setErrorInfo(int errorCode, String errorMessage) {
        if (errorCode > 0) {
            logger.warn( "SQL_ERR[{}]: {}", errorCode, errorMessage );
        }
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @return 错误编码
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * @return 错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return 是否走主库
     */
    public boolean isMasterQuery() {
        return isMasterQuery;
    }

    /**
     * @param master 是否走主库
     */
    public void setMasterQuery(boolean master) {
        isMasterQuery = master;
    }

    /**
     * @return 单节点 sqlInfo（多节点场景为 null）
     */
    public SqlInfo getSqlInfo() {
        return sqlInfo;
    }

    /**
     * @param sqlInfo 单节点 sqlInfo
     */
    public void setSqlInfo(SqlInfo sqlInfo) {
        this.sqlInfo = sqlInfo;
    }

    /**
     * @return 多节点 sqlInfo 列表（单节点场景为 null）
     */
    public List<SqlInfo> getSqlInfoList() {
        return sqlInfoList;
    }

    /**
     * @param sqlInfoList 多节点 sqlInfo 列表
     */
    public void setSqlInfoList(List<SqlInfo> sqlInfoList) {
        this.sqlInfoList = sqlInfoList;
    }

    /**
     * 单条可执行 SQL 信息，绑定目标 {@link DataTable} 与改写后的 SQL 文本。
     * 由 {@link SqlParser#generateSqlInfo()} 构造，传给 {@link uw.mydb.proxy.mysql.MySqlSession#addCommand} 执行。
     */
    public static class SqlInfo {

        /**
         * 目标数据表（含 DataNode + table 名）。
         */
        private DataTable dataTable;

        /**
         * 改写过程中的 SQL 缓冲，{@link #getNewSql()} 首次调用时 toString 后置 null 释放。
         */
        private StringBuilder newSqlBuf;

        /**
         * 最终改写后的 SQL 文本，{@link #getNewSql()} 懒构造。
         */
        private String newSql;

        /**
         * @param sqlSize 预分配 StringBuilder 容量（通常为 sourceSql.length + 余量）
         */
        public SqlInfo(int sqlSize) {
            newSqlBuf = new StringBuilder( sqlSize );
        }

        /**
         * @param dataTable 目标数据表
         * @param newSql    已构造好的 SQL 文本
         */
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

        /**
         * @return 目标集群 ID（来自 dataTable.dataNode）
         */
        public long getClusterId() {
            return dataTable.getDataNode().getClusterId();
        }

        /**
         * @return 目标 database（来自 dataTable.dataNode）
         */
        public String getDatabase() {
            return dataTable.getDataNode().getDatabase();
        }

        /**
         * @return 目标物理表名（来自 dataTable.table，可能已被分片改名）
         */
        public String getTable() {
            return dataTable.getTable();
        }

        /**
         * @return 目标数据表
         */
        public DataTable getDataTable() {
            return dataTable;
        }

        /**
         * @param dataTable 目标数据表
         */
        public void setDataTable(DataTable dataTable) {
            this.dataTable = dataTable;
        }

        /**
         * 获取改写后的 SQL。懒构造：首次调用时把 {@link #newSqlBuf} 转为字符串并置 null，之后调用直接返回缓存。
         *
         * @return 改写后的 SQL 文本
         */
        public String getNewSql() {
            if (newSql == null) {
                newSql = newSqlBuf.toString();
                newSqlBuf = null;
            }
            return newSql;
        }

        /**
         * 向 SQL 缓冲追加文本片段。
         *
         * @param text 文本片段
         * @return 当前 SqlInfo（链式调用）
         */
        public SqlInfo appendSql(String text) {
            this.newSqlBuf.append( text );
            return this;
        }


    }


}
