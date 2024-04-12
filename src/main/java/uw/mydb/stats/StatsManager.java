package uw.mydb.stats;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import uw.mydb.conf.MydbConfigService;
import uw.mydb.conf.MydbProperties;
import uw.mydb.constant.SQLType;
import uw.mydb.mysql.MySqlClient;
import uw.mydb.proxy.ProxySessionManager;
import uw.mydb.stats.vo.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 统计的工厂类。
 *
 * @author axeon
 */
public class StatsManager {


    /**
     * 报告异步线程池。
     */
    private static final ThreadPoolExecutor reportExecutor = new ThreadPoolExecutor( 5, 500, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setDaemon( true ).setNameFormat( "report-executor-%d" ).build(), new ThreadPoolExecutor.CallerRunsPolicy() );

    /**
     * 服务器统计表。
     */
    private static SqlStats proxySqlStats = new SqlStats();

    /**
     * 基于访问库表的统计表。
     */
    private static Map<String, SchemaSqlStats> sqlStatsMap = new ConcurrentHashMap();

    /**
     * 获得server Sql统计。
     *
     * @return
     */
    public static SqlStats getProxySqlStats() {
        return proxySqlStats;
    }

    /**
     * 统计慢sql。
     */
    public static void statsSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, boolean isSuccess, int rowNum, long txBytes,
                                long rxBytes, long exeMillis, long runDate) {
        //获得schema统计表。
        SchemaSqlStats schemaSqlStats =
                sqlStatsMap.computeIfAbsent( new StringBuilder( 60 ).append( clusterId ).append( '.' ).append( serverId ).append( '.' ).append( database ).append( '.' ).append( table ).toString(), s -> new SchemaSqlStats( clusterId, serverId, database, table ) );
        if (sqlType == SQLType.SELECT.getValue()) {
            schemaSqlStats.addSelectNum( 1 );
            proxySqlStats.addSelectErrorNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addSelectErrorNum( 1 );
                proxySqlStats.addSelectErrorNum( 1 );
            }
            schemaSqlStats.addSelectExeMillis( exeMillis );
            proxySqlStats.addSelectExeMillis( exeMillis );
            schemaSqlStats.addSelectRowNum( rowNum );
            proxySqlStats.addSelectRowNum( rowNum );
            schemaSqlStats.addSelectTxBytes( txBytes );
            proxySqlStats.addSelectTxBytes( txBytes );
            schemaSqlStats.addSelectRxBytes( rxBytes );
            proxySqlStats.addSelectRxBytes( rxBytes );
        } else if (sqlType == SQLType.INSERT.getValue()) {
            schemaSqlStats.addInsertNum( 1 );
            proxySqlStats.addInsertErrorNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addInsertErrorNum( 1 );
                proxySqlStats.addInsertErrorNum( 1 );
            }
            schemaSqlStats.addInsertExeMillis( exeMillis );
            proxySqlStats.addInsertExeMillis( exeMillis );
            schemaSqlStats.addInsertRowNum( rowNum );
            proxySqlStats.addInsertRowNum( rowNum );
            schemaSqlStats.addInsertTxBytes( txBytes );
            proxySqlStats.addInsertTxBytes( txBytes );
            schemaSqlStats.addInsertRxBytes( rxBytes );
            proxySqlStats.addInsertRxBytes( rxBytes );
        } else if (sqlType == SQLType.UPDATE.getValue()) {
            schemaSqlStats.addUpdateNum( 1 );
            proxySqlStats.addUpdateErrorNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addUpdateErrorNum( 1 );
                proxySqlStats.addUpdateErrorNum( 1 );
            }
            schemaSqlStats.addUpdateExeMillis( exeMillis );
            proxySqlStats.addUpdateExeMillis( exeMillis );
            schemaSqlStats.addUpdateRowNum( rowNum );
            proxySqlStats.addUpdateRowNum( rowNum );
            schemaSqlStats.addUpdateTxBytes( txBytes );
            proxySqlStats.addUpdateTxBytes( txBytes );
            schemaSqlStats.addUpdateRxBytes( rxBytes );
            proxySqlStats.addUpdateRxBytes( rxBytes );
        } else if (sqlType == SQLType.DELETE.getValue()) {
            schemaSqlStats.addDeleteNum( 1 );
            proxySqlStats.addDeleteErrorNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addDeleteErrorNum( 1 );
                proxySqlStats.addDeleteErrorNum( 1 );
            }
            schemaSqlStats.addDeleteExeMillis( exeMillis );
            proxySqlStats.addDeleteExeMillis( exeMillis );
            schemaSqlStats.addDeleteRowNum( rowNum );
            proxySqlStats.addDeleteRowNum( rowNum );
            schemaSqlStats.addDeleteTxBytes( txBytes );
            proxySqlStats.addDeleteTxBytes( txBytes );
            schemaSqlStats.addDeleteRxBytes( rxBytes );
            proxySqlStats.addDeleteRxBytes( rxBytes );
        } else {
            schemaSqlStats.addOtherNum( 1 );
            proxySqlStats.addOtherErrorNum( 1 );
            if (!isSuccess) {
                schemaSqlStats.addOtherErrorNum( 1 );
                proxySqlStats.addOtherErrorNum( 1 );
            }
            schemaSqlStats.addOtherExeMillis( exeMillis );
            proxySqlStats.addOtherExeMillis( exeMillis );
            schemaSqlStats.addOtherRowNum( rowNum );
            proxySqlStats.addOtherRowNum( rowNum );
            schemaSqlStats.addOtherTxBytes( txBytes );
            proxySqlStats.addOtherTxBytes( txBytes );
            schemaSqlStats.addOtherRxBytes( rxBytes );
            proxySqlStats.addOtherRxBytes( rxBytes );
        }
        if (exeMillis >= MydbConfigService.getMydbProperties().getSlowQueryMillis()) {
            SlowSql slowSql = new SlowSql( clientIp, clusterId, serverId, database, table, sql, sqlType, rowNum, txBytes, rxBytes, exeMillis, runDate );
            MydbConfigService.reportSlowSql( slowSql );
            reportExecutor.submit( () -> MydbConfigService.reportSlowSql( slowSql ) );
        }
    }

    /**
     * 记录错误SQL。
     */
    public static void reportErrorSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, int rowNum, long txBytes, long rxBytes, long exeMillis, long runDate, int errorCode, String errorMsg, String exception) {
        ErrorSql errorSql = new ErrorSql( clientIp, clusterId, serverId, database, table, sql, sqlType, rowNum, txBytes, rxBytes, exeMillis, runDate, errorCode, errorMsg, exception);
        reportExecutor.submit( () -> MydbConfigService.reportErrorSql( errorSql ) );
    }

    /**
     * 返回mydb服务状态。
     *
     * @return
     */
    public static ProxyRunReport getProxyRunStats() {
        //获得按主机分组统计的map。
        ProxyRunReport report = new ProxyRunReport();
        report.setProxyId( MydbConfigService.getProxyId() );
        MydbProperties properties = MydbConfigService.getMydbProperties();
        report.setProxyHost( properties.getProxyHost() );
        report.setProxyPort( properties.getProxyPort() );
        report.setAppName( properties.getAppName() );
        report.setAppVersion( properties.getAppVersion() );
        //设置内存和线程信息。
        Runtime runtime = Runtime.getRuntime();
        report.setJvmMemMax( runtime.maxMemory() );
        report.setJvmMemTotal( runtime.totalMemory() );
        report.setJvmMemFree( runtime.freeMemory() );
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        report.setThreadActive( threadMXBean.getThreadCount() );
        report.setThreadDaemon( threadMXBean.getDaemonThreadCount() );
        report.setThreadPeak( threadMXBean.getPeakThreadCount() );
        report.setThreadStarted( threadMXBean.getTotalStartedThreadCount() );
        //统计信息。
        report.setConnectionNum( ProxySessionManager.getConnectionNum() );
        report.setClientConnMap( ProxySessionManager.getClientConnMap() );
        report.setMysqlConnList( MySqlClient.getMysqlConnList() );
        report.setProxySqlStats( proxySqlStats );
        report.setSchemaSqlStatsList( sqlStatsMap.values() );
        return report;
    }

}