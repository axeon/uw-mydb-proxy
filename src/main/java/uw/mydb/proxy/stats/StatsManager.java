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
 * 统计的工厂类。
 *
 * @author axeon
 */
public class StatsManager {


    /**
     * 报告异步线程池。
     */
    private static final ThreadPoolExecutor reportExecutor = new ThreadPoolExecutor( 1, 100, 180L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setDaemon( true ).setNameFormat( "report-executor-%d" ).build(), new ThreadPoolExecutor.CallerRunsPolicy() );

    /**
     * 服务器统计表。
     */
    private static final ProxyRunStats proxyRunStats = new ProxyRunStats();

    /**
     * 基于访问库表的统计表。
     */
    private static final Map<String, SchemaRunStats> schemaRunStatsMap = new ConcurrentHashMap();

    /**
     * 统计慢sql。
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
            MydbProxyConfigService.reportSlowSql( slowSql );
            reportExecutor.submit( () -> MydbProxyConfigService.reportSlowSql( slowSql ) );
        }
    }

    /**
     * 记录错误SQL。
     */
    public static void reportErrorSql(String clientIp, long clusterId, long serverId, String database, String table, String sql, int sqlType, int rowNum, long txBytes,
                                      long rxBytes, long exeMillis, long runDate, int errorCode, String errorMsg, String exception) {
        ErrorSql errorSql = new ErrorSql( clientIp, clusterId, serverId, database, table, sql, sqlType, rowNum, txBytes, rxBytes, exeMillis, runDate, errorCode, errorMsg+"\n"+exception );
        reportExecutor.submit( () -> MydbProxyConfigService.reportErrorSql( errorSql ) );
    }


    /**
     * 报告schema运行统计。。
     */
    public static void reportSchemaRunStats() {
        List<SchemaRunStats> schemaRunStatsList = schemaRunStatsMap.values().stream().filter( x -> x.checkReportSchema() ).toList();
        if (schemaRunStatsList.size() > 0) {
            MydbProxyConfigService.reportSchemaRunStats( schemaRunStatsList );
        }
    }


    /**
     * 报告proxy运行统计。
     *
     * @return
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