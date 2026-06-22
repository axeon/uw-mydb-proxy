package uw.mydb.proxy.stats;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import uw.mydb.common.report.*;
import uw.mydb.proxy.conf.MydbProxyConfigService;
import uw.mydb.proxy.conf.MydbProxyProperties;
import uw.mydb.proxy.constant.SQLType;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.server.ProxySessionManager;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * proxy 运行统计管理器，全静态方法。
 * <p>
 * 维护两份聚合数据：
 * <ul>
 *   <li>{@link #proxyRunStats}：当前 proxy 实例的全局统计（SELECT/INSERT/UPDATE/DELETE 的次数、错误数、行数、字节数、耗时累计）。</li>
 *   <li>{@link #schemaRunStatsMap}：按 {@code clusterId.serverId.database.table} 维度细分的统计，key 形如 "1.100.uw_demo.t_user"。</li>
 * </ul>
 * 上报：
 * <ul>
 *   <li>慢 SQL 与错误 SQL 通过 {@link #reportExecutor}（daemon 线程池）异步上报 center，避免阻塞业务线程。</li>
 *   <li>{@link #reportProxyRunStats} / {@link #reportSchemaRunStats} 由 {@link uw.mydb.proxy.server.ProxyServer} 的定时任务周期触发。</li>
 * </ul>
 * 线程安全：所有计数使用原子方法，Map 使用 {@link ConcurrentHashMap}。
 *
 * @author axeon
 */
public class StatsManager {


    /**
     * 异步上报线程池（daemon，核心 1 / 最大 100，SynchronousQueue + CallerRunsPolicy）。
     * 满载时退化为调用方线程同步上报（业务线程，但上报频率低，可接受）。
     */
    private static final ThreadPoolExecutor reportExecutor = new ThreadPoolExecutor( 1, 100, 180L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setDaemon( true ).setNameFormat( "report-executor-%d" ).build(), new ThreadPoolExecutor.CallerRunsPolicy() );

    /**
     * 当前 proxy 实例的全局运行统计快照，周期性上报后由 SchemaRunStats/ProxyRunStats 内部自减重置（具体语义见对应类）。
     */
    private static final ProxyRunStats proxyRunStats = new ProxyRunStats();

    /**
     * 按库表细分的运行统计，key = "clusterId.serverId.database.table"。
     */
    private static final Map<String, SchemaRunStats> schemaRunStatsMap = new ConcurrentHashMap<>();

    /**
     * 统计一次 SQL 执行。
     * <p>
     * 按 sqlType 分流到 SELECT/INSERT/UPDATE/DELETE/OTHER 各项计数器（次数、错误数、行数、字节数、耗时累计），
     * 同时累计到 proxyRunStats 与对应 schemaRunStats；耗时超过 {@link MydbProxyProperties#getSlowQueryMillis} 时异步上报慢 SQL。
     *
     * @param clientIp   客户端 IP
     * @param clusterId  目标集群 ID
     * @param serverId   目标 server ID
     * @param database   目标 database
     * @param table      目标表
     * @param sql        SQL 文本（仅慢 SQL 上报时使用）
     * @param sqlType    SQL 类型（{@link SQLType#getValue()}）
     * @param isSuccess  是否成功
     * @param rowNum     行数
     * @param txBytes    发送字节数
     * @param rxBytes    接收字节数
     * @param exeMillis  执行耗时（毫秒）
     * @param runDate    执行时间戳
     */
    public static void statsSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, boolean isSuccess, int rowNum,
                                long txBytes, long rxBytes, long exeMillis, long runDate) {
        //获取schema统计表。
        SchemaRunStats schemaSqlStats =
                schemaRunStatsMap.computeIfAbsent( new StringBuilder( 60 ).append( clusterId ).append( '.' ).append( serverId ).append( '.' ).append( database ).append( '.' ).append( table ).toString(), s -> new SchemaRunStats( MydbProxyConfigService.getProxyId(), clusterId, serverId, database, table ) );
        //设置更新标记，用于优化。
        schemaSqlStats.updateReportStatus();
        if (sqlType == SQLType.SELECT.getValue()) {
            schemaSqlStats.addSelectNum( 1 );
            proxyRunStats.addSelectNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addSelectErrorNum( 1 );
                proxyRunStats.addSelectErrorNum( 1 );
            }
            schemaSqlStats.addSelectExeMillis( exeMillis );
            proxyRunStats.addSelectExeMillis( exeMillis );
            schemaSqlStats.addSelectRowNum( rowNum );
            proxyRunStats.addSelectRowNum( rowNum );
            schemaSqlStats.addSelectTxBytes( txBytes );
            proxyRunStats.addSelectTxBytes( txBytes );
            schemaSqlStats.addSelectRxBytes( rxBytes );
            proxyRunStats.addSelectRxBytes( rxBytes );
        } else if (sqlType == SQLType.INSERT.getValue()) {
            schemaSqlStats.addInsertNum( 1 );
            proxyRunStats.addInsertNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addInsertErrorNum( 1 );
                proxyRunStats.addInsertErrorNum( 1 );
            }
            schemaSqlStats.addInsertExeMillis( exeMillis );
            proxyRunStats.addInsertExeMillis( exeMillis );
            schemaSqlStats.addInsertRowNum( rowNum );
            proxyRunStats.addInsertRowNum( rowNum );
            schemaSqlStats.addInsertTxBytes( txBytes );
            proxyRunStats.addInsertTxBytes( txBytes );
            schemaSqlStats.addInsertRxBytes( rxBytes );
            proxyRunStats.addInsertRxBytes( rxBytes );
        } else if (sqlType == SQLType.UPDATE.getValue()) {
            schemaSqlStats.addUpdateNum( 1 );
            proxyRunStats.addUpdateNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addUpdateErrorNum( 1 );
                proxyRunStats.addUpdateErrorNum( 1 );
            }
            schemaSqlStats.addUpdateExeMillis( exeMillis );
            proxyRunStats.addUpdateExeMillis( exeMillis );
            schemaSqlStats.addUpdateRowNum( rowNum );
            proxyRunStats.addUpdateRowNum( rowNum );
            schemaSqlStats.addUpdateTxBytes( txBytes );
            proxyRunStats.addUpdateTxBytes( txBytes );
            schemaSqlStats.addUpdateRxBytes( rxBytes );
            proxyRunStats.addUpdateRxBytes( rxBytes );
        } else if (sqlType == SQLType.DELETE.getValue()) {
            schemaSqlStats.addDeleteNum( 1 );
            proxyRunStats.addDeleteNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addDeleteErrorNum( 1 );
                proxyRunStats.addDeleteErrorNum( 1 );
            }
            schemaSqlStats.addDeleteExeMillis( exeMillis );
            proxyRunStats.addDeleteExeMillis( exeMillis );
            schemaSqlStats.addDeleteRowNum( rowNum );
            proxyRunStats.addDeleteRowNum( rowNum );
            schemaSqlStats.addDeleteTxBytes( txBytes );
            proxyRunStats.addDeleteTxBytes( txBytes );
            schemaSqlStats.addDeleteRxBytes( rxBytes );
            proxyRunStats.addDeleteRxBytes( rxBytes );
        } else {
            schemaSqlStats.addOtherNum( 1 );
            proxyRunStats.addOtherNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addOtherErrorNum( 1 );
                proxyRunStats.addOtherErrorNum( 1 );
            }
            schemaSqlStats.addOtherExeMillis( exeMillis );
            proxyRunStats.addOtherExeMillis( exeMillis );
            schemaSqlStats.addOtherRowNum( rowNum );
            proxyRunStats.addOtherRowNum( rowNum );
            schemaSqlStats.addOtherTxBytes( txBytes );
            proxyRunStats.addOtherTxBytes( txBytes );
            schemaSqlStats.addOtherRxBytes( rxBytes );
            proxyRunStats.addOtherRxBytes( rxBytes );
        }
        if (exeMillis >= MydbProxyConfigService.getMydbProperties().getSlowQueryMillis()) {
            SlowSql slowSql = new SlowSql( clientIp, clusterId, serverId, database, table, sql, sqlType, rowNum, txBytes, rxBytes, exeMillis, runDate );
            reportExecutor.submit( () -> MydbProxyConfigService.reportSlowSql( slowSql ) );
        }
    }

    /**
     * 异步上报错误 SQL 到 center（通过 {@link #reportExecutor}）。
     *
     * @param clientIp   客户端 IP
     * @param clusterId  集群 ID
     * @param serverId   server ID
     * @param database   database
     * @param table      表
     * @param sql        SQL 文本
     * @param sqlType    SQL 类型
     * @param rowNum     行数
     * @param txBytes    发送字节
     * @param rxBytes    接收字节
     * @param exeMillis  执行耗时
     * @param runDate    执行时间戳
     * @param errorCode  错误码
     * @param errorMsg   错误信息
     * @param exception  异常栈（附加到 errorMsg 后）
     */
    public static void reportErrorSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, int rowNum, long txBytes,
                                      long rxBytes, long exeMillis, long runDate, int errorCode, String errorMsg, String exception) {
        ErrorSql errorSql = new ErrorSql( clientIp, clusterId, serverId, database, table, sql, sqlType, rowNum, txBytes, rxBytes, exeMillis, runDate, errorCode, errorMsg+"\n"+exception );
        reportExecutor.submit( () -> MydbProxyConfigService.reportErrorSql( errorSql ) );
    }


    /**
     * 上报所有标记为"有更新"的 schema 维度统计到 center（每小时一次，由 ProxyServer 调度）。
     */
    public static void reportSchemaRunStats() {
        List<SchemaRunStats> schemaRunStatsList = schemaRunStatsMap.values().stream().filter( x -> x.checkReportSchema() ).toList();
        if (schemaRunStatsList.size() > 0) {
            MydbProxyConfigService.reportSchemaRunStats( schemaRunStatsList );
        }
    }


    /**
     * 上报当前 proxy 实例的运行统计到 center（每分钟一次，由 ProxyServer 调度）。
     * <p>
     * 采集内容包括：proxyId/configKey/host/port、CPU 负载、JVM 内存、线程数、客户端连接（按 IP 聚合 + 总数）、
     * 后端 MySQL 连接（busy/idle/list）、活跃 schema 数量，以及累计的 SQL 计数。
     */
    public static void reportProxyRunStats() {
        //获取按主机分组统计的map。
        proxyRunStats.setProxyId( MydbProxyConfigService.getProxyId() );
        MydbProxyProperties properties = MydbProxyConfigService.getMydbProperties();
        proxyRunStats.setConfigKey( properties.getConfigKey() );
        proxyRunStats.setProxyHost( properties.getProxyHost() );
        proxyRunStats.setProxyPort( properties.getProxyPort() );
        proxyRunStats.setProxyName( properties.getAppName() );
        proxyRunStats.setProxyVersion( properties.getAppVersion() );
        //设置CPU内存和线程信息。
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        proxyRunStats.setCpuLoad( osMxBean.getSystemLoadAverage() / osMxBean.getAvailableProcessors() * 100 );
        Runtime runtime = Runtime.getRuntime();
        proxyRunStats.setJvmMemMax( runtime.maxMemory() );
        proxyRunStats.setJvmMemTotal( runtime.totalMemory() );
        proxyRunStats.setJvmMemFree( runtime.freeMemory() );
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        proxyRunStats.setThreadActive( threadMXBean.getThreadCount() );
        proxyRunStats.setThreadDaemon( threadMXBean.getDaemonThreadCount() );
        proxyRunStats.setThreadPeak( threadMXBean.getPeakThreadCount() );
        proxyRunStats.setThreadStarted( threadMXBean.getTotalStartedThreadCount() );
        //统计信息。
        proxyRunStats.setClientConnMap( ProxySessionManager.getClientConnMap() );
        proxyRunStats.setClientNum( proxyRunStats.getClientConnMap().size() );
        proxyRunStats.setClientConnNum( ProxySessionManager.getConnectionNum() );
        MysqlConnStats mysqlConnStats = MySqlClient.getMysqlConnStats();
        proxyRunStats.setMysqlNum( mysqlConnStats.getMysqlNum() );
        proxyRunStats.setMysqlBusyConnNum( (int) mysqlConnStats.getMysqlBusyConnNum() );
        proxyRunStats.setMysqlIdleConnNum( (int) mysqlConnStats.getMysqlIdleConnNum() );
        proxyRunStats.setMysqlConnList( mysqlConnStats.getMysqlConnList() );
        proxyRunStats.setSchemaStatsNum( (int) schemaRunStatsMap.values().stream().filter( x -> x.checkReportProxy() ).count() );
        MydbProxyConfigService.reportProxyRunStats( proxyRunStats );
    }

}