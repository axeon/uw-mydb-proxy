package uw.mydb.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * MySQL集群切换类型
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MysqlClusterSwitchMode {

    /**
     * 不切换。
     */
    NOT_SWITCH( 0, "不切换" ),

    /**
     * 切换
     */
    SWITCH( 1, "切换" );


    /**
     * 参数值
     */
    private int value;

    /**
     * 参数信息。
     */
    private String label;

    MysqlClusterSwitchMode(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static MysqlClusterSwitchMode findByValue(int value) {
        for (MysqlClusterSwitchMode e : MysqlClusterSwitchMode.values()) {
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
