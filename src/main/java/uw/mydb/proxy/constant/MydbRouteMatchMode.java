package uw.mydb.proxy.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 路由匹配模式枚举，决定 SQL 未携带分片键时 proxy 的行为。
 * <p>
 * 对应 {@link uw.mydb.common.conf.TableConfig#getMatchType()} 字段。
 * {@code @JsonFormat(shape = OBJECT)} 使其序列化为 {"value":0,"label":"精确匹配"} 形式。
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MydbRouteMatchMode {

    /**
     * 精确匹配（value=0）：SQL 必须携带可解析的分片键值才能路由，否则返回无法匹配错误。适用于强约束分片场景。
     */
    MATCH_FIX(0, "精确匹配"),

    /**
     * 默认匹配（value=1）：SQL 未携带分片键时路由到 baseNode 默认节点。
     */
    MATCH_DEFAULT(1, "默认匹配"),

    /**
     * 全量匹配（value=2）：SQL 未携带分片键时路由到全部分片（广播查询），代价较高，慎用。
     */
    MATCH_ALL(2, "全部匹配");

    /**
     * 枚举数值，对应 TableConfig.matchType 字段。
     */
    private int value;

    /**
     * 枚举中文显示名。
     */
    private String label;

    MydbRouteMatchMode(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * @return 枚举数值
     */
    public int getValue() {
        return value;
    }

    /**
     * @return 枚举显示名
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据 value 反查枚举。
     *
     * @param value 数值
     * @return 对应枚举，找不到返回 null
     */
    public static MydbRouteMatchMode findByValue(int value) {
        for (MydbRouteMatchMode e : MydbRouteMatchMode.values()) {
            if (value == e.value) {
                return e;
            }
        }
        return null;
    }

}
