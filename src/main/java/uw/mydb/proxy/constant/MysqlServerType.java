package uw.mydb.proxy.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * MYSQL类型
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MysqlServerType {

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

    MysqlServerType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static MysqlServerType findByValue(int value) {
        for (MysqlServerType e : MysqlServerType.values()) {
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
