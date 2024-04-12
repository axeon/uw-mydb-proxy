package uw.mydb.stats;

import uw.mydb.stats.vo.ProxyRunInfo;
import uw.mydb.stats.vo.SlowSql;
import uw.mydb.stats.vo.SqlStats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统计的工厂类。
 *
 * @author axeon
 */
public class StatsManager {


    /**
     * 服务器统计表。
     */
    private static SqlStats proxySqlStats = new SqlStats();

    /**
     * 基于访问库表的统计表。
     */
    private static Map<String, SqlStats> sqlStatsMap = new ConcurrentHashMap();

    /**
     * 获得server Sql统计。
     *
     * @return
     */
    public static SqlStats getProxySqlStats() {
        return proxySqlStats;
    }

    /**
     * 获得schema统计。
     *
     * @return
     */
    public static Map<String, SqlStats> getSqlStatsMap() {
        return sqlStatsMap;
    }

    /**
     * 统计慢sql。
     */
    public static void stats(String clientIp, long clusterId, long serverId, String database, String table, String sql, String sqlType, boolean isSuccess, int rowNum, long txBytes, long rxBytes, long exeMillis, long runDate) {
        //获得schema统计表。
        SqlStats ssp = sqlStatsMap.computeIfAbsent( new StringBuilder( 60 ).append( clusterId ).append( '.' ).append( serverId ).append( '.' ).append( database ).append( '.' ).append( table ).toString(), s -> new SqlStats() );
        SlowSql slowSql = new SlowSql(clientIp,  clusterId,  serverId,  database,  table,  sql,  sqlType,  rowNum,  txBytes,  rxBytes,  exeMillis, runDate);
    }

    /**
     * 返回mydb服务状态。
     *
     * @return
     */
    public static ProxyRunInfo getProxyRunStats() {
        //获得按主机分组统计的map。
        return new ProxyRunInfo();
    }

}