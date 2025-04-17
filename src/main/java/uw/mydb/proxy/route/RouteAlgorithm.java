package uw.mydb.proxy.route;

import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.RouteConfig;
import uw.mydb.common.conf.TableConfig;

import java.security.PrivilegedActionException;
import java.util.*;

/**
 * 动态分表算法，一般来说表是完全动态创建的。
 *
 * @author axeon
 */
public abstract class RouteAlgorithm {

    /**
     * 路由配置。
     */
    protected RouteConfig routeConfig;

    /**
     * 算法路由参数初始化。
     */
    public void init(RouteConfig routeConfig) {
        this.routeConfig = routeConfig;
    }

    /**
     * 参数配置。
     * 子类通过继承实现配置化。
     */
    public abstract void config();

    /**
     * 路由算法名称。
     */
    public abstract String name();

    /**
     * 路由算法描述。
     */
    public abstract String description();

    /**
     * 根据给定的值，计算出归属表名。
     * 此方法一般增，删，改用。
     *
     * @param tableConfig
     * @param routeInfo   携带初始值的路由信息
     * @param value       分表数值
     * @return 修正后的路由信息
     */
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        throw new UnsupportedOperationException();
    }

    /**
     * 根据给定的值，计算出归属表名。
     * 此方法一般查询用。
     *
     * @param tableConfig
     * @param dataTable  携带初始值的路由信息
     * @return 修正后的路由信息
     */
    public Set<DataTable> calculate(TableConfig tableConfig, DataTable dataTable, List<String> values) throws RouteException {
        LinkedHashSet set = new LinkedHashSet();
        for (String value : values) {
            set.add( calculate( tableConfig, dataTable, value ) );
        }
        return set;
    }

    /**
     * 根据给定之后，计算出所有表名。
     * 此方法一般查询用。
     *
     * @param tableConfig
     * @param dataTable  携带初始值的路由信息
     * @param startValue
     * @param endValue
     * @return 表名列表
     */
    public List<DataTable> calculateRange(TableConfig tableConfig, DataTable dataTable, String startValue, String endValue) throws RouteException {
        throw new RouteException( "不支持范围计算!" );
    }

    public String getRouteKey() {
        return routeConfig.getRouteKey();
    }

    /**
     * 获取预设的表名信息。
     * 返回集合中String。
     * 默认使用分库设置，加载一次。
     *
     * @return
     */
    public DataTable getDefaultRoute(TableConfig tableConfig, DataTable routeInfo) throws RouteException {
        return routeInfo;
    }

    /**
     * 获取预设的表名信息。
     * 返回集合中String。
     * 默认使用分库设置，加载一次。
     *
     * @return
     */
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return routeInfos;
    }

    /**
     * 存放路由Key和匹配到的数据。
     */
    public static class RouteData {

        /**
         * key，用于优化内存占用。
         */
        private String key;

        /**
         * value，用于优化内存占用。
         */
        private RouteValue value;

        /**
         * key，用于优化内存占用。
         */
        private String key1;

        /**
         * value，用于优化内存占用。
         */
        private RouteValue value1;

        /**
         * key，用于优化内存占用。
         */
        private String key2;

        /**
         * value，用于优化内存占用。
         */
        private RouteValue value2;

        /**
         * 检查是否有key。
         *
         * @return
         */
        public boolean checkKeyExists() {
            if (key != null) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * 是否完全是空值。
         *
         * @return
         */
        public boolean isEmptyValue() {
            if (value==null||value.isEmpty()||value1==null||value2.isEmpty()||value2==null||value2.isEmpty()){
                return true;
            }else{
                return false;
            }
        }

        /**
         * 返回key列表。
         *
         * @return
         */
        public String keyString() {
            StringBuilder sb = new StringBuilder();
            if (key != null) {
                sb.append( key );
            }
            if (key1 != null) {
                sb.append( ',' ).append( key1 );
            }
            if (key2 != null) {
                sb.append( ',' ).append( key2 );
            }
            return sb.toString();
        }

        /**
         * 是否为单一数值。
         *
         * @return
         */
        public boolean isSingle() {
            return key2 != null;
        }

        /**
         * 获取单一值列表。
         *
         * @return
         */
        public RouteValue getValue() {
            return value;
        }

        /**
         * 获取数值列表
         *
         * @return
         */
        public RouteValue[] getValues() {
            if (key == null) {
                return new RouteValue[0];
            }
            if (key1 == null) {
                return new RouteValue[]{value};
            }
            if (key2 == null) {
                return new RouteValue[]{value, value1};
            }
            return new RouteValue[]{value, value1, value2};
        }

        /**
         * 初始化key
         *
         * @param key
         */
        public void initKey(String key) {
            if (this.key == null) {
                this.key = key;
                this.value = new RouteValue();
            } else if (this.key1 == null) {
                this.key1 = key;
                this.value1 = new RouteValue();
            } else if (this.key2 == null) {
                this.key2 = key;
                this.value2 = new RouteValue();
            }
        }

        /**
         * 获取数值。
         *
         * @param key
         * @return
         */
        public RouteValue getValue(String key) {
            if (this.key == null) {
                return null;
            } else if (this.key.equals( key )) {
                return value;
            }
            if (key1 == null) {
                return null;
            } else if (this.key1.equals( key )) {
                return value1;
            }
            if (this.key2.equals( key )) {
                return value2;
            }
            return null;
        }
    }

    /**
     * 用于存储路由算法异常。
     */
    public static class RouteException extends Exception {

        /**
         * Constructs a new exception with {@code null} as its detail message.
         * The cause is not initialized, and may subsequently be initialized by a
         * call to {@link #initCause}.
         */
        public RouteException() {
        }

        /**
         * Constructs a new exception with the specified detail message.  The
         * cause is not initialized, and may subsequently be initialized by
         * a call to {@link #initCause}.
         *
         * @param message the detail message. The detail message is saved for
         *                later retrieval by the {@link #getMessage()} method.
         */
        public RouteException(String message) {
            super( message );
        }

        /**
         * Constructs a new exception with the specified detail message and
         * cause.  <p>Note that the detail message associated with
         * {@code cause} is <i>not</i> automatically incorporated in
         * this exception's detail message.
         *
         * @param message the detail message (which is saved for later retrieval
         *                by the {@link #getMessage()} method).
         * @param cause   the cause (which is saved for later retrieval by the
         *                {@link #getCause()} method).  (A <tt>null</tt> value is
         *                permitted, and indicates that the cause is nonexistent or
         *                unknown.)
         * @since 1.4
         */
        public RouteException(String message, Throwable cause) {
            super( message, cause );
        }

        /**
         * Constructs a new exception with the specified cause and a detail
         * message of <tt>(cause==null ? null : cause.toString())</tt> (which
         * typically contains the class and detail message of <tt>cause</tt>).
         * This constructor is useful for exceptions that are little more than
         * wrappers for other throwables (for example, {@link
         * PrivilegedActionException}).
         *
         * @param cause the cause (which is saved for later retrieval by the
         *              {@link #getCause()} method).  (A <tt>null</tt> value is
         *              permitted, and indicates that the cause is nonexistent or
         *              unknown.)
         * @since 1.4
         */
        public RouteException(Throwable cause) {
            super( cause );
        }

        /**
         * Constructs a new exception with the specified detail message,
         * cause, suppression enabled or disabled, and writable stack
         * trace enabled or disabled.
         *
         * @param message            the detail message.
         * @param cause              the cause.  (A {@code null} value is permitted,
         *                           and indicates that the cause is nonexistent or unknown.)
         * @param enableSuppression  whether or not suppression is enabled
         *                           or disabled
         * @param writableStackTrace whether or not the stack trace should
         *                           be writable
         * @since 1.7
         */
        public RouteException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super( message, cause, enableSuppression, writableStackTrace );
        }
    }

    /**
     * 路由数值数据。
     */
    public static class RouteValue {

        /**
         * 空值
         */
        public static final int NULL = 0;

        /**
         * 单个数值
         */
        public static final int SINGLE = 1;

        /**
         * RANGE类型
         */
        public static final int RANGE = 2;

        /**
         * 多值类型
         */
        public static final int MULTI = 3;

        /**
         * 类型
         */
        private int type = NULL;

        /**
         * 数值1
         */
        private String value;

        /**
         * 数值2
         */
        private String valueEnd;

        /**
         * 多值类型
         */
        private List<String> values;

        /**
         * 是否为空。
         *
         * @return
         */
        public boolean isEmpty() {
            switch (type) {
                case SINGLE:
                    return value == null;
                case RANGE:
                    return value == null && valueEnd == null;
                case MULTI:
                    return values == null;
                default:
                    return true;
            }
        }

        /**
         * 针对范围运行有时候只有一个值来进行优化。
         */
        public void guessType() {
            if (value == null || valueEnd == null) {
                type = SINGLE;
                if (value == null && valueEnd != null) {
                    value = valueEnd;
                }
                if (value != null && valueEnd == null) {
                    valueEnd = value;
                }
            }
        }


        public void putValue(String value) {
            type = SINGLE;
            this.value = value;
        }

        public void putRangeStart(String valueStart) {
            type = RANGE;
            this.value = valueStart;
        }

        public void putRangeEnd(String valueEnd) {
            type = RANGE;
            this.valueEnd = valueEnd;
        }

        public void putValues(List<String> values) {
            type = MULTI;
            this.values = values;
        }

        public String getValueStart() {
            return value;
        }

        public String getValueEnd() {
            return valueEnd;
        }

        public List<String> getValues() {
            return values;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }
    }


    /**
     * 用于优化存储RouteInfo。
     * 对大多数情况下，可能只有单一路由信息，尽量减少不必要的List使用。
     */
    public static class RouteResult {

        /**
         * 单一路由信息。
         */
        private DataTable dataTable;

        /**
         * 路由信息集合。
         */
        private Set<DataTable> dataTables;

        /**
         * 设置一个RouteInfo Set.
         *
         * @param dataTables
         */
        public void setAll(Set<DataTable> dataTables) {
            if (dataTables != null && dataTables.size() == 1) {
                this.dataTable = dataTables.iterator().next();
                this.dataTables = null;
            } else {
                this.dataTable = null;
                this.dataTables = dataTables;
            }
        }

        /**
         * 返回是否单条数据。
         *
         * @return
         */
        public boolean isSingle() {
            return dataTables == null;
        }

        public void setSingle(DataTable dataTable) {
            this.dataTable = dataTable;
            this.dataTables = null;
        }

        public DataTable getDataTable() {
            return dataTable;
        }

        public Set<DataTable> getDataTables() {
            return dataTables;
        }
    }

}
