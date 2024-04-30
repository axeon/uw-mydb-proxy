package uw.mydb.proxy.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * MySQL集群切换类型
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MydbRouteMatchMode {

    /**
     * 精确匹配。
     */
    MATCH_FIX(0, "精确匹配"),

    /**
     * 匹配默认值，如果没有匹配值，可以匹配到默认值上。
     */
    MATCH_DEFAULT(1, "匹配默认值"),

    /**
     * 匹配全部值，如果没有匹配值，则匹配所有数值。
     */
    MATCH_ALL(1, "匹配全部值");

    /**
     * 参数值
     */
    private int value;

    /**
     * 参数信息。
     */
    private String label;

    MydbRouteMatchMode(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static MydbRouteMatchMode findByValue(int value) {
        for (MydbRouteMatchMode e : MydbRouteMatchMode.values()) {
            if (value == e.value) {
                return e;
            }
        }
        return null;
    }

}
