package uw.mydb.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * MYSQL类型
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MysqlClusterType {

    /**
     * 主。
     */
    MASTER( 0, "主" ),

    /**
     * 从
     */
    SLAVE( 1, "从" );

    /**
     * 参数值
     */
    private int value;

    /**
     * 参数信息。
     */
    private String label;

    MysqlClusterType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static MysqlClusterType findByValue(int value) {
        for (MysqlClusterType e : MysqlClusterType.values()) {
            if (value == e.value) {
                return e;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

}
