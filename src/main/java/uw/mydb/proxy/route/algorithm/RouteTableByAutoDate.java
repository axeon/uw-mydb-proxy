package uw.mydb.proxy.route.algorithm;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.route.RouteAlgorithm;
import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.TableConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据给定的日期，给出归属表名，支持动态自动建表。
 * 参数：
 * date-pattern: 可以不指定，设置为日期来源格式
 * format-pattern：格式化成的样式
 *
 * @author axeon
 */
public class RouteTableByAutoDate extends RouteAlgorithm {

    private static final Logger log = LoggerFactory.getLogger( RouteTableByAutoDate.class );

    /**
     * 日期数据格式。
     */
    private DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );
    /**
     * 格式化的格式。
     */
    private DateTimeFormatter FORMAT_PATTERN = null;

    /**
     * 格式化的样式字符串。
     */
    private String FORMAT_PATTERN_CODE = "yyyy";

    @Override
    public void config() {
        Map<String, String> params = routeConfig.getRouteParamMap();

        //formatPattern,格式化的日期时间格式
        String formatPattern = params.get( "format-pattern" );
        if (StringUtils.isNotBlank( formatPattern )) {
            if ("yyyyMMdd".equals( formatPattern ) || "yyyyMM".equals( formatPattern ) || "yyyy".equals( formatPattern ) || "yyMMdd".equals( formatPattern ) || "yyMM".equals( formatPattern ) || "yy".equals( formatPattern ) || "MMdd".equals( formatPattern ) || "MM".equals( formatPattern ) || "dd".equals( formatPattern )) {
                FORMAT_PATTERN_CODE = formatPattern;
            }
        }
        FORMAT_PATTERN = DateTimeFormatter.ofPattern( FORMAT_PATTERN_CODE );
    }

    /**
     * 路由名称。
     */
    @Override
    public String name() {
        return "自动日期分表路由";
    }

    /**
     * 路由描述。
     */
    @Override
    public String description() {
        return null;
    }

    @Override
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        //优先选择快速格式化
        String table = quickFormat( routeInfo.getTable(), value, FORMAT_PATTERN_CODE );
        routeInfo.setTable( table );
        return routeInfo;
    }

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
        List<DataTable> newList = new ArrayList<>();
        for (String text : list) {
            DataTable copy = dataTable.copy();
            copy.setTable( new StringBuilder( dataTable.getTable() ).append( "_" ).append( text ).toString() );
            newList.add( copy );
        }
        return newList;
    }

    /**
     * 默认导向到最新的日期分片。
     *
     * @param tableConfig
     * @param routeInfo
     * @return
     */
    @Override
    public DataTable getDefaultRoute(TableConfig tableConfig, DataTable routeInfo) throws RouteException {
        //此处有性能问题，最好缓存当前时间
        String now = LocalDateTime.now().format( DATE_PATTERN );
        return calculate( tableConfig, routeInfo, now );
    }

    /**
     * 最大化适配解析。
     *
     * @return
     */
    private LocalDateTime fitParse(String dateValue) {
        if (dateValue.length() > 19) {
            dateValue = dateValue.substring( 0, 19 );
        }
        return LocalDateTime.parse( dateValue, DATE_PATTERN );
    }

    /**
     * 日期类型快速格式化。
     * 大部分的日期，其实都是yyyyMMdd格式的，按照这个格式走就好。
     *
     * @return
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
