package uw.mydb.proxy.route;

import uw.mydb.common.conf.DataTable;
import uw.mydb.common.conf.RouteConfig;
import uw.mydb.common.conf.TableConfig;

import java.security.PrivilegedActionException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分库分表路由算法抽象基类。
 *
 * <h2>职责</h2>
 * 定义路由算法的统一契约：接收原始路由信息（{@link DataTable}）、一个或多个分片键值，
 * 输出修正后的路由信息（精确到 clusterId/database/table）。具体算法由子类实现，
 * 通过 {@code RouteConfig.routeAlgorithm} 指定的类名反射实例化，并由 {@link RouteManager}
 * 按 routeId 组装成"算法链"顺序执行。
 *
 * <h2>在路由体系中的角色</h2>
 * <ul>
 *   <li>每条 {@code RouteConfig} 对应一个算法实例，{@code parentId} 用于把多个算法串成算法链
 *       （例如先按 SaaS 分库，再按日期分表）。</li>
 *   <li>算法仅负责"给定值 -> 数据节点/表名"的映射计算，不负责库表是否存在校验（由
 *       {@code MydbProxyConfigService.ensureTableExists} 兜底）。</li>
 *   <li>同一算法实例会被同 routeId 的所有请求共享，子类的 {@link #config()} 应把可变状态
 *       收敛为不可变或线程安全结构。</li>
 * </ul>
 *
 * <h2>配置参数（routeParamMap）</h2>
 * key/value 由子类自定义，基类自身不读取 routeParamMap。常见 key 见各子类文档
 * （如 {@code baseNode}、{@code routeList}、{@code datePattern} 等）。
 *
 * <h2>典型用法示例</h2>
 * <pre>{@code
 * // 算法链：RouteDatabaseBySaas(分库) -> RouteTableByAutoDate(按日分表)
 * RouteAlgorithm algorithm = new RouteTableByAutoDate();
 * algorithm.init(routeConfig);   // 注入 RouteConfig
 * algorithm.config();            // 子类解析 routeParamMap
 * DataTable hit = algorithm.calculate(tableConfig, defaultRoute, "2026-06-22 10:00:00");
 * }</pre>
 *
 * <h2>内部类语义</h2>
 * <ul>
 *   <li>{@link RouteData} —— 路由入参容器，最多承载 3 组 (routeKey, RouteValue) 槽位
 *       (key/value, key1/value1, key2/value2)，对应算法链上最多 3 个算法的分片键。</li>
 *   <li>{@link RouteValue} —— 单个分片键的数值状态机，type 取值为
 *       {@link RouteValue#NULL}/{@link RouteValue#SINGLE}/{@link RouteValue#RANGE}/{@link RouteValue#MULTI}，
 *       决定 {@link RouteManager} 走哪条计算分支。</li>
 *   <li>{@link RouteResult} —— 算法链的输出，通过 isSingle 区分单条路由与多条路由集合，
 *       用于内部存储优化：单条直接持引用，多条才分配 Set。</li>
 *   <li>{@link RouteException} —— 算法执行期间的语义错误（参数缺失、范围超限、不支持范围计算等），
 *       由 RouteManager 转译为对外响应。</li>
 * </ul>
 *
 * @author axeon
 */
public abstract class RouteAlgorithm {

    /**
     * 当前算法对应的路由配置。
     * 由 {@link #init(RouteConfig)} 注入，子类在 {@link #config()} 中读取其 routeParamMap/routeKey 等。
     * 生命周期内只写入一次，后续只读，线程安全。
     */
    protected RouteConfig routeConfig;

    /**
     * 注入路由配置并完成算法实例初始化的第一步。
     * 必须在 {@link #config()} 之前调用，否则子类无法读取 routeParamMap。
     *
     * @param routeConfig 路由配置，不可为 null。包含算法类名、routeKey、parentId、routeParamMap 等。
     */
    public void init(RouteConfig routeConfig) {
        this.routeConfig = routeConfig;
    }

    /**
     * 子类解析 {@link RouteConfig#getRouteParamMap()} 并构建自身查找结构的钩子。
     * 在 {@link #init(RouteConfig)} 之后由 {@link RouteManager} 调用一次。
     * 子类应把解析结果存为不可变结构（如 final Map、预构造的 List），以保证多线程安全。
     */
    public abstract void config();

    /**
     * 算法短名称，用于管理端展示与日志。不应包含换行。
     *
     * @return 算法名称，非 null、非空
     */
    public abstract String name();

    /**
     * 算法描述文本，应包含 routeParamMap 的 key 列表与典型用法。
     *
     * @return 多行描述，允许包含换行；非 null
     */
    public abstract String description();

    /**
     * 根据单一分片键值，计算出归属路由。
     * 默认实现抛出 {@link UnsupportedOperationException}，表示该算法不支持单值计算
     * （例如纯 RANGE 算法）。增删改语句通常走此入口。
     *
     * @param tableConfig 当前逻辑表的配置（含 routeId、matchType、tableName 等），非 null
     * @param routeInfo   上一步累积得到的路由信息，算法在此基础上覆盖 database/table；非 null
     * @param value       分片键字符串值，非 null。数值/日期/hash key 等都按字符串传入，由子类解析
     * @return 修正后的路由信息（通常返回同一个 routeInfo 实例，亦可返回新实例）
     * @throws RouteException 当 value 无法被该算法识别、或参数缺失、或不支持单值计算时抛出
     */
    public DataTable calculate(TableConfig tableConfig, DataTable routeInfo, String value) throws RouteException {
        throw new UnsupportedOperationException();
    }

    /**
     * 根据一组分片键值，计算出归属路由集合（MULTI 模式入口）。
     * 基类默认实现：对每个 value 调用 {@link #calculate(TableConfig, DataTable, String)}，
     * 用 {@link LinkedHashSet} 去重并保持插入顺序。子类可重写以做批量优化。
     *
     * @param tableConfig 当前逻辑表配置，非 null
     * @param dataTable   基准路由信息，非 null
     * @param values      多个分片键值，非 null、可为空列表（空列表返回空集合）
     * @return 命中的路由集合，非 null；可能为空集
     * @throws RouteException 任一 value 计算失败时抛出
     */
    public Set<DataTable> calculate(TableConfig tableConfig, DataTable dataTable, List<String> values) throws RouteException {
        LinkedHashSet<DataTable> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add( calculate( tableConfig, dataTable, value ) );
        }
        return set;
    }

    /**
     * 根据范围分片键值，计算出区间内全部命中的路由（RANGE 模式入口）。
     * 查询语句 WHERE col BETWEEN startValue AND endValue 时由 {@link RouteManager} 调用。
     * 基类默认抛出 {@link RouteException}，表示该算法不支持范围计算（如 hash/mod 类）。
     *
     * @param tableConfig 当前逻辑表配置，非 null
     * @param dataTable   基准路由信息，非 null
     * @param startValue  起始值（含），可 null；为 null 时由子类处理（通常等同于 endValue）
     * @param endValue    结束值（含），可 null；为 null 时通常等同于 startValue
     * @return 区间内命中的路由列表，非 null；可能为空。返回 null 表示算法跳过，由调用方处理
     * @throws RouteException 当算法不支持范围计算，或区间过大被拒绝（如日期超过 {@code MAX_ROUTE_COUNT}）时抛出
     */
    public List<DataTable> calculateRange(TableConfig tableConfig, DataTable dataTable, String startValue, String endValue) throws RouteException {
        throw new RouteException( "不支持范围计算!" );
    }

    /**
     * 返回该算法绑定的分片键名称（即 SQL 中需要路由的列名）。
     * {@link RouteManager} 据此从 {@link RouteData} 中取出对应的 {@link RouteValue}。
     *
     * @return routeKey，非 null；其值来源于 {@link RouteConfig#getRouteKey()}
     */
    public String getRouteKey() {
        return routeConfig.getRouteKey();
    }

    /**
     * 当 {@link RouteData} 中没有匹配上本算法 routeKey 的值、且 {@code matchType=MATCH_DEFAULT} 时
     * 调用此方法，给出兜底路由。
     * <p>基类默认直接返回传入的 routeInfo（不修改），表示算法不做兜底。子类如需指定
     * 默认节点（如日期算法兜底到"今天"的表），应重写此方法。</p>
     *
     * @param tableConfig 当前逻辑表配置，非 null
     * @param routeInfo   当前累积的路由信息，非 null
     * @return 兜底路由，非 null
     * @throws RouteException 当无法给出兜底时抛出
     */
    public DataTable getDefaultRoute(TableConfig tableConfig, DataTable routeInfo) throws RouteException {
        return routeInfo;
    }

    /**
     * 当 {@code matchType=MATCH_ALL} 时调用，返回本算法所有可能的路由目标。
     * <p>基类默认直接返回传入的 routeInfos，表示算法未额外枚举。子类（如 hash/preset）应重写，
     * 返回算法配置中的全部表，供 MATCH_ALL 全表扫描使用。</p>
     *
     * @param tableConfig 当前逻辑表配置，非 null
     * @param routeInfos  上游已收集的路由列表，非 null；算法可向其追加
     * @return 完整的路由列表，非 null
     * @throws RouteException 当无法枚举时抛出
     */
    public List<DataTable> getAllRouteList(TableConfig tableConfig, List<DataTable> routeInfos) throws RouteException {
        return routeInfos;
    }

    /**
     * 路由入参容器，存放算法链上每个算法的 routeKey 与对应匹配到的 {@link RouteValue}。
     *
     * <h3>槽位机制</h3>
     * 采用固定三槽 (key/value, key1/value1, key2/value2) 而非 Map，是内存占用优化：
     * 绝大多数 SQL 只涉及 1~3 个分片键，避免 HashMap 的对象头开销。槽位按
     * {@link #initKey(String)} 调用顺序填充，超过 3 个 routeKey 会被静默忽略
     * （意味着算法链最多 3 个算法参与路由）。
     *
     * <h3>线程安全</h3>
     * RouteData 单次请求内构造、单线程消费，非线程安全，不应跨请求复用。
     */
    public static class RouteData {

        /**
         * 第 1 个槽位的 routeKey（即第 1 个算法的分片键列名）。
         * null 表示该槽位未启用。一旦在 {@link #initKey(String)} 中赋值不再变更。
         */
        private String key;

        /**
         * 第 1 个槽位的数值容器，与 {@link #key} 同生命周期。非 null 当且仅当 key 非 null。
         */
        private RouteValue value;

        /**
         * 第 2 个槽位的 routeKey。null 表示未启用第 2 个算法。
         */
        private String key1;

        /**
         * 第 2 个槽位的数值容器。非 null 当且仅当 key1 非 null。
         */
        private RouteValue value1;

        /**
         * 第 3 个槽位的 routeKey。null 表示未启用第 3 个算法。
         */
        private String key2;

        /**
         * 第 3 个槽位的数值容器。非 null 当且仅当 key2 非 null。
         */
        private RouteValue value2;

        /**
         * 是否已设置至少一个 routeKey 槽位。
         *
         * @return true 表示至少设置了第 1 个槽位（算法链非空）
         */
        public boolean checkKeyExists() {
            if (key != null) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * 是否所有"已启用"槽位的值都为空。
         * 未启用的槽位（key1/key2 为 null）不参与判断，等同于"不影响整体空值判定"。
         * 用于 {@link RouteManager} 判断是否走 matchType 兜底分支。
         *
         * @return true 表示所有已启用槽位的 {@link RouteValue#isEmpty()} 均为 true
         */
        public boolean isEmptyValue() {
            boolean vEmpty = value == null || value.isEmpty();
            //key1未设置时，value1不参与判断（视为不影响整体空值判定）。
            boolean v1Empty = key1 == null || value1 == null || value1.isEmpty();
            boolean v2Empty = key2 == null || value2 == null || value2.isEmpty();
            return vEmpty && v1Empty && v2Empty;
        }

        /**
         * 把所有已启用的 routeKey 用逗号拼接，用于日志展示。
         *
         * @return 形如 "userId,saasId,createDate" 的字符串；无可显示 key 时返回空串
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
         * 是否仅启用了单一 routeKey 槽位。
         * 语义为"算法链只有 1 个算法"，等价于 key 非 null 且 key1 未启用。
         *
         * @return true 表示仅第 1 个槽位启用
         */
        public boolean isSingle() {
            return key != null && key1 == null;
        }

        /**
         * 获取第 1 个槽位的数值容器。
         * 仅当确定算法链只有 1 个算法时使用；多算法场景应使用 {@link #getValue(String)}。
         *
         * @return 第 1 个槽位的 RouteValue；未设置 key 时为 null
         */
        public RouteValue getValue() {
            return value;
        }

        /**
         * 返回所有已启用槽位的数值容器数组。
         * 顺序对应槽位 1/2/3。
         *
         * @return 非 null 数组；未启用任何槽位时返回长度为 0 的空数组
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
         * 按 {@link RouteManager} 遍历算法链的顺序填充下一个槽位。
         * 已填满 3 个槽位后再调用将被静默忽略（丢弃第 4 个及以后的算法 routeKey）。
         *
         * @param key 当前算法的 routeKey，非 null
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
         * 按 routeKey 精确匹配取数值容器。
         *
         * @param key 要查找的 routeKey，非 null
         * @return 命中槽位的 RouteValue；key 未启用或不匹配时返回 null
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
     * 路由算法执行期间的受检异常。
     * 典型场景：分片值无法被算法识别、范围过大被拒绝、不支持 RANGE 计算、参数配置错误。
     * 由 {@link RouteManager#calculate} 抛出并交由上层 Controller 转译为对外错误码，
     * 不应携带序列化框架或 SQL 内部信息。
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
     * 单个 routeKey 的数值容器与状态机。
     *
     * <h3>四种 type 状态</h3>
     * <ul>
     *   <li>{@link #NULL} —— 未填充任何值，{@link #isEmpty()} 返回 true。</li>
     *   <li>{@link #SINGLE} —— 单值，由 {@link #putValue(String)} 设置或由
     *       {@link #guessType()} 从 RANGE 退化而来；对应 WHERE col = ? 等值查询，
     *       {@link RouteManager} 调用 {@link RouteAlgorithm#calculate}。</li>
     *   <li>{@link #RANGE} —— 范围值，由 {@link #putRangeStart(String)} / {@link #putRangeEnd(String)} 设置；
     *       对应 BETWEEN / &gt;= / &lt;=，{@link RouteManager} 调用
     *       {@link RouteAlgorithm#calculateRange}。两端中有一端为 null 时由
     *       {@link #guessType()} 退化为 SINGLE。</li>
     *   <li>{@link #MULTI} —— 多值列表，由 {@link #putValues(List)} 设置；
     *       对应 IN(?)，{@link RouteManager} 调用 {@link RouteAlgorithm#calculate(TableConfig, DataTable, List)} 批量。</li>
     * </ul>
     *
     * <h3>线程安全</h3>
     * 非线程安全，单次请求构造单线程消费。
     */
    public static class RouteValue {

        /**
         * 空值类型常量：尚未填充任何数据。
         */
        public static final int NULL = 0;

        /**
         * 单值类型常量：等值匹配，对应 {@code WHERE col = ?}。
         */
        public static final int SINGLE = 1;

        /**
         * 范围类型常量：包含起止值，对应 {@code WHERE col BETWEEN ? AND ?}。
         */
        public static final int RANGE = 2;

        /**
         * 多值类型常量：对应 {@code WHERE col IN (?, ?...)}。
         */
        public static final int MULTI = 3;

        /**
         * 当前状态，初始 {@link #NULL}。由各 put* 方法维护。
         */
        private int type = NULL;

        /**
         * SINGLE 模式的值；也作为 RANGE 模式的起始值（含）。
         */
        private String value;

        /**
         * RANGE 模式的结束值（含）。SINGLE 模式下不使用。
         */
        private String valueEnd;

        /**
         * MULTI 模式的多值列表。仅 type=MULTI 时有效。
         */
        private List<String> values;

        /**
         * 根据当前 type 判定是否等同于"无值可用"。
         * <ul>
         *   <li>SINGLE：value == null 即为空</li>
         *   <li>RANGE：value 与 valueEnd 同时为 null 才算空（单端 null 仍可由 guessType 修正）</li>
         *   <li>MULTI：values == null 即为空</li>
         *   <li>NULL 或其它：始终为空</li>
         * </ul>
         * {@link RouteManager} 据此决定是否走 matchType 兜底分支。
         *
         * @return true 表示当前槽位没有可用值
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
         * RANGE 模式下若只有一端有值，则降级为 SINGLE 的优化。
         * <p>判定逻辑：value 或 valueEnd 任一为 null，则把 type 改成 SINGLE，
         * 并把非空端复制到另一端，使 {@link #getValueStart()} 永远非 null。
         * 当两端都为 null 时退化为 SINGLE 但 value 仍为 null（视为空值）。</p>
         * 由 {@link RouteManager} 在分发前主动调用。
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


        /**
         * 设置为 SINGLE 单值。覆盖之前的状态。
         *
         * @param value 等值匹配的字符串值，非 null
         */
        public void putValue(String value) {
            type = SINGLE;
            this.value = value;
        }

        /**
         * 设置/更新 RANGE 起始值（含），并把 type 切到 RANGE。
         *
         * @param valueStart 起始值字符串，非 null
         */
        public void putRangeStart(String valueStart) {
            type = RANGE;
            this.value = valueStart;
        }

        /**
         * 设置/更新 RANGE 结束值（含），并把 type 切到 RANGE。
         *
         * @param valueEnd 结束值字符串，非 null
         */
        public void putRangeEnd(String valueEnd) {
            type = RANGE;
            this.valueEnd = valueEnd;
        }

        /**
         * 设置为 MULTI 多值，把 type 切到 MULTI。
         *
         * @param values 值列表，非 null、可为空列表
         */
        public void putValues(List<String> values) {
            type = MULTI;
            this.values = values;
        }

        /**
         * 返回 SINGLE 的值，或 RANGE 的起始值（含）。
         *
         * @return 起始/单值字符串，可能为 null（未设置时）
         */
        public String getValueStart() {
            return value;
        }

        /**
         * 返回 RANGE 的结束值（含）。
         *
         * @return 结束值字符串，可能为 null
         */
        public String getValueEnd() {
            return valueEnd;
        }

        /**
         * 返回 MULTI 模式的值列表。
         *
         * @return 值列表，仅 type=MULTI 时非 null
         */
        public List<String> getValues() {
            return values;
        }

        /**
         * 返回当前状态机 type。
         *
         * @return 取值为 {@link #NULL}/{@link #SINGLE}/{@link #RANGE}/{@link #MULTI} 之一
         */
        public int getType() {
            return type;
        }

        /**
         * 直接覆盖 type。一般只在状态修正（如 guessType 之外的特殊场景）使用。
         *
         * @param type 新的 type 常量
         */
        public void setType(int type) {
            this.type = type;
        }
    }


    /**
     * 算法链的计算结果容器，针对"绝大多数路由只有 1 个命中"做存储优化。
     * <p>内部用单引用 {@link #dataTable} 与集合 {@link #dataTables} 互斥表示：
     * 命中数为 1 时只占用 1 个引用字段，命中数 &gt; 1 或 0 时使用 Set。</p>
     *
     * <h3>状态</h3>
     * <ul>
     *   <li>单条：dataTables == null，dataTable 为命中表（或 null 表示尚未计算）</li>
     *   <li>多条：dataTable == null，dataTables 为命中集合（可能为空集表示 0 命中）</li>
     * </ul>
     * 通过 {@link #setSingle(DataTable)} / {@link #setAll(Set)} 切换；
     * {@link #setAll(Set)} 会自动把 size==1 的集合折叠成单引用。
     *
     * <h3>线程安全</h3>
     * 非线程安全，单次请求内由 {@link RouteManager} 单线程构造与消费。
     */
    public static class RouteResult {

        /**
         * 单条命中时的路由信息。dataTables != null 时本字段保持 null。
         */
        private DataTable dataTable;

        /**
         * 多条命中时的路由集合；为 null 表示当前是"单条模式"。
         */
        private Set<DataTable> dataTables;

        /**
         * 设置多条命中结果。
         * 若集合恰好只有 1 个元素，自动折叠为单引用模式以节省内存。
         *
         * @param dataTables 命中集合，可 null（视作 0 命中，进入多条模式但集合为 null）
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
         * 当前是否为单条模式。
         *
         * @return true 表示当前命中的是单条路由（即便 dataTable 可能为 null）
         */
        public boolean isSingle() {
            return dataTables == null;
        }

        /**
         * 设置单条命中结果，同时清空 dataTables。
         *
         * @param dataTable 命中的路由信息，可为 null（表示尚未计算/默认路由）
         */
        public void setSingle(DataTable dataTable) {
            this.dataTable = dataTable;
            this.dataTables = null;
        }

        /**
         * 单条模式下取命中路由。
         *
         * @return 单条命中；多条模式下返回 null
         */
        public DataTable getDataTable() {
            return dataTable;
        }

        /**
         * 多条模式下取命中集合。
         *
         * @return 命中集合；单条模式下返回 null
         */
        public Set<DataTable> getDataTables() {
            return dataTables;
        }
    }

}
