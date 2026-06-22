package uw.mydb.proxy.route.algorithm;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.common.conf.DataNode;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;
import uw.mydb.proxy.route.RouteAlgorithm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自动日期分表路由：按日期字段的值给表名追加日期后缀，支持按日/月/年粒度切分。
 *
 * <h2>职责</h2>
 * 修改 {@link DataTable} 的 {@code table}（即表名），不改库。表名规则：
 * <pre>
 *   {原始表名}_{日期后缀}
 *   例如 order -> order_20260622  (datePattern=yyyyMMdd)
 *   例如 order -> order_202606    (datePattern=yyyyMM)
 *   例如 order -> order_2026      (datePattern=yyyy)
 * </pre>
 *
 * <h2>配置参数（routeParamMap）</h2>
 * <ul>
 *   <li>{@code datePattern}：分表后缀粒度，取值白名单 {@code yyyy / yyyyMM / yyyyMMdd /
 *       yy / yyMM / yyMMdd / MM / MMdd / dd} 之一；不指定或不命中白名单时默认 {@code yyyy}。</li>
 *   <li>{@code baseNode}：可选的基础 DataNode（{@code clusterId.database}）。本算法不直接使用，
 *       仅在链上配合其它算法时存在。</li>
 * </ul>
 *
 * <h2>输入日期值格式</h2>
 * 计算时期望入参 value 形如 {@code "yyyy-MM-dd HH:mm:ss"}（标准 LocalDateTime 字符串）。
 * {@link #quickFormat} 会按字符位置切片，而非严格解析，因此也兼容短日期。
 * {@link #calculateRange} 会通过 {@link #fitParse} 严格按 {@code "yyyy-MM-dd HH:mm:ss"} 解析，
 * 超过 19 字符的部分会被截断。
 *
 * <h2>典型用法示例</h2>
 * <pre>
 * // 表 order 按日切分
 * routeParamMap: datePattern=yyyyMMdd
 * algorithm.calculate(tableConfig, routeInfo, "2026-06-22 10:00:00")
 *   -> routeInfo.table = "order_20260622"
 * </pre>
 *
 * @author axeon
 */
public class RouteTableByAutoDate extends RouteAlgorithm {

    /**
     * 范围计算时单次返回的最大表数量上限（防止 BETWEEN 跨度过大导致笛卡尔爆炸）。
     * 超出会在 {@link #calculateRange} 抛 {@link RouteException}。
     */
    private static final int MAX_ROUTE_COUNT = 1000;

    /**
     * 入参日期字符串的解析格式，固定 {@code "yyyy-MM-dd HH:mm:ss"}。
     * 非线程安全字段，但因 {@link #fitParse} 在多线程下不会被并发调用
     * （DateTimeFormatter 本身是线程安全的，这里只是声明引用）。
     */
    private DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
    /**
     * 实际用于格式化分表后缀的 formatter，由 {@link #config()} 根据 {@link #FORMAT_PATTERN_CODE} 构造。
     * null 表示尚未调用 config()。
     */
    private DateTimeFormatter FORMAT_PATTERN = null;

    /**
     * 分表后缀粒度代码，初始 {@code "yyyy"}。必须是 datePattern 白名单之一。
     */
    private String FORMAT_PATTERN_CODE = "yyyy";

    /**
     * 基础数据节点，来自 routeParamMap.baseNode。本算法不直接使用，仅保留以备链上其它算法使用。
     */
    private DataNode baseNode;

    /**
     * 解析 routeParamMap：读取 {@code baseNode} 与 {@code datePattern}（白名单校验），
     * 构造 {@link #FORMAT_PATTERN} 与 {@link #FORMAT_PATTERN_CODE}。
     * 非 datePattern 白名单的取值会被忽略，回退为默认 {@code "yyyy"}。
     */
    @Override
    public void config() {
        Map<String, String> params = routeConfig.getRouteParamMap();
        baseNode = new DataNode( params.get( "baseNode" ) );
        //formatPattern,格式化的日期时间格式
        String formatPattern = params.get( "datePattern" );
        if (StringUtils.isNotBlank( formatPattern )) {
            if ("yyyyMMdd".equals( formatPattern ) || "yyyyMM".equals( formatPattern ) || "yyyy".equals( formatPattern ) || "yyMMdd".equals( formatPattern ) || "yyMM".equals( formatPattern ) || "yy".equals( formatPattern ) || "MMdd".equals( formatPattern ) || "MM".equals( formatPattern ) || "dd".equals( formatPattern )) {
                FORMAT_PATTERN_CODE = formatPattern;
            }
        }
        FORMAT_PATTERN = DateTimeFormatter.ofPattern( FORMAT_PATTERN_CODE );
    }

    /**
     * 路由名称。用于管理端展示。
     *
     * @return 固定字符串 "自动日期分表路由"
     */
    @Override
    public String name() {
        return "自动日期分表路由";
    }

    /**
     * 算法描述与参数说明。
     *
     * @return 多行文本，含 datePattern 取值与 baseNode 说明
     */
    @Override
    public String description() {
        return """
                根据给定的日期，给出归属表名，支持动态自动建表。
                参数说明:
                datePattern：日期分表后缀。一般可以设定为yyyy, yyyyMM, yyyyMMdd。
                baseNode: 默认基础节点，可以不指定。
                """;
    }

    /**
     * 按单值计算路由表：用 {@link #quickFormat} 给表名追加日期后缀。
     * 不修改 routeInfo.dataNode。
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param routeInfo   待追加后缀的路由信息，非 null；其 table 字段会被覆盖
     * @param value       日期字符串，期望形如 {@code "yyyy-MM-dd HH:mm:ss"}；非 null
     * @return 入参 routeInfo（表名已加后缀）
     * @throws RouteException 本实现不主动抛出（quickFormat 静默截取），由上层捕获异常值
     */
    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        //优先选择快速格式化
        String table = quickFormat( routeInfo.getTable(), value, FORMAT_PATTERN_CODE );
        routeInfo.setTable( table );
        return routeInfo;
    }

    /**
     * 按日期范围枚举所有命中分表，用于 BETWEEN 查询。
     * <p>步长由 {@link #FORMAT_PATTERN_CODE} 决定：含 {@code dd} 走日步长，含 {@code MM} 走月步长，
     * 否则走年步长。最终列表长度若超过 {@link #MAX_ROUTE_COUNT} 抛 RouteException 防爆。</p>
     *
     * @param tableConfig 逻辑表配置，非 null（本算法未直接使用）
     * @param dataTable   模板路由信息，非 null；每个命中表都基于 {@link DataTable#copy()} 复制
     * @param startValue  起始日期（含），可 null（取 endValue）；期望 {@code "yyyy-MM-dd HH:mm:ss"}
     * @param endValue    结束日期（含），可 null（取 startValue）；两者同为 null 时返回 null
     * @return 命中分表列表，非 null；起止都为 null 时返回 null（由上层处理）
     * @throws RouteException 当命中表数 &gt; {@link #MAX_ROUTE_COUNT} 时抛出
     */
    @Override
    public List<DataTable> calculateRange(TableConfig tableConfig, DataTable dataTable, String startValue, String endValue) throws RouteException {
        if (startValue == null) {
            startValue = endValue;
        }
        if (endValue == null) {
            endValue = startValue;
        }
        if (startValue == null && endValue == null) {
            return null;
        }
        List<String> list = new ArrayList<>();
        LocalDateTime startDate, endDate;
        startDate = fitParse( startValue );
        endDate = fitParse( endValue );
        //判定先后顺序
        while (startDate.compareTo( endDate ) <= 0) {
            list.add( startDate.format( FORMAT_PATTERN ) );
            //针对间隔的优化判定
            if (FORMAT_PATTERN_CODE.contains( "dd" )) {
                startDate = startDate.plusDays( 1 );
            } else if (FORMAT_PATTERN_CODE.contains( "MM" )) {
                startDate = startDate.plusMonths( 1 );
            } else if (FORMAT_PATTERN_CODE.contains( "yy" )) {
                startDate = startDate.plusYears( 1 );
            }
        }
        String endText = endDate.format( FORMAT_PATTERN );
        if (!list.contains( endText )) {
            list.add( endText );
        }
        //循环赋值
        if (list.size() > MAX_ROUTE_COUNT) {
            throw new RouteException( "日期范围路由数量超过限制: " + list.size() + " > " + MAX_ROUTE_COUNT );
        }
        List<DataTable> newList = new ArrayList<>();
        for (String text : list) {
            DataTable copy = dataTable.copy();
            copy.setTable( new StringBuilder( dataTable.getTable() ).append( "_" ).append( text ).toString() );
            newList.add( copy );
        }
        return newList;
    }

    /**
     * 当 matchType=MATCH_DEFAULT 且未提供分片值时，兜底路由到"当前时刻"所在的分表。
     * <p>每次调用都取 {@code LocalDateTime.now()}，未做缓存，高频调用下有一定开销，
     * 但可保证兜底目标随时间推进自动切到新分表（如跨日零点切到新表）。</p>
     *
     * @param tableConfig 逻辑表配置，非 null
     * @param routeInfo   待追加后缀的路由信息，非 null
     * @return 命中当前时间的分表路由；委托给 {@link #calculate}
     * @throws RouteException 由 {@link #calculate} 抛出
     */
    @Override
    public DataTable getDefaultRoute(TableConfig tableConfig, DataTable routeInfo) throws RouteException {
        //此处有性能问题，最好缓存当前时间
        String now = LocalDateTime.now().format( DATE_PATTERN );
        return calculate( tableConfig, routeInfo, now );
    }

    /**
     * 适配性日期解析：截断到前 19 个字符后按 {@code "yyyy-MM-dd HH:mm:ss"} 解析。
     *
     * @param dateValue 原始日期字符串，非 null
     * @return 解析得到的 LocalDateTime
     * @throws java.time.format.DateTimeParseException 字符串格式非法时由 {@link LocalDateTime#parse} 抛出（非 RouteException）
     */
    private LocalDateTime fitParse(String dateValue) {
        if (dateValue.length() > 19) {
            dateValue = dateValue.substring( 0, 19 );
        }
        return LocalDateTime.parse( dateValue, DATE_PATTERN );
    }

    /**
     * 按字符位置切片的快速日期后缀格式化，避免严格的 LocalDateTime 解析开销。
     * <p>仅在入参长度充足时切片取值；长度不足则不追加任何后缀（返回纯 tableName）。
     * 支持的 formatPattern：yyyyMMdd / yyyyMM / yyyy / yyMMdd / yyMM / yy / MMdd / MM / dd。</p>
     *
     * @param tableName    原始表名，非 null
     * @param value        入参日期字符串，期望形如 {@code "yyyy-MM-dd HH:mm:ss"}；非 null
     * @param formatPattern 分表后缀粒度代码
     * @return 形如 {@code "tableName_YYYYMMDD"} 的最终表名
     */
    private String quickFormat(String tableName, String value, String formatPattern) {
        StringBuilder sb = new StringBuilder( tableName ).append( "_" );
        switch (formatPattern) {
            case "yyyyMMdd":
                if (value.length() >= 10) {
                    sb.append( value.substring( 0, 4 ) ).append( value.substring( 5, 7 ) ).append( value.substring( 8, 10 ) );
                }
                break;
            case "yyyyMM":
                if (value.length() >= 7) {
                    sb.append( value.substring( 0, 4 ) ).append( value.substring( 5, 7 ) );
                }
                break;
            case "yyyy":
                if (value.length() >= 4) {
                    sb.append( value.substring( 0, 4 ) );
                }
                break;
            case "yyMMdd":
                if (value.length() >= 10) {
                    sb.append( value.substring( 2, 4 ) ).append( value.substring( 5, 7 ) ).append( value.substring( 8, 10 ) );
                }
                break;
            case "yyMM":
                if (value.length() >= 7) {
                    sb.append( value.substring( 2, 4 ) ).append( value.substring( 5, 7 ) );
                }
                break;
            case "yy":
                if (value.length() >= 4) {
                    sb.append( value.substring( 2, 4 ) );
                }
                break;
            case "MMdd":
                if (value.length() >= 10) {
                    sb.append( value.substring( 5, 7 ) ).append( value.substring( 8, 10 ) );
                }
                break;
            case "MM":
                if (value.length() >= 7) {
                    sb.append( value.substring( 5, 7 ) );
                }
                break;
            case "dd":
                if (value.length() >= 10) {
                    sb.append( value.substring( 8, 10 ) );
                }
                break;
            default:
                break;
        }
        return sb.toString();
    }

}
