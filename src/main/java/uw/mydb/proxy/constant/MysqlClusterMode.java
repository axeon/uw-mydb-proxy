package uw.mydb.proxy.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * MYSQL集群模式
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum MysqlClusterMode {

    /**
     * 单机模式。
     */
    SINGLE_NODE( 0, "单机模式" ),

    /**
     * 主从模式
     */
    MASTER_SLAVE( 1, "主从模式" ),

    /**
     * 主主模式
     */
    MASTER_MASTER( 2, "主主模式" );


    /**
     * 参数值
     */
    private int value;

    /**
     * 参数信息。
     */
    private String label;

    MysqlClusterMode(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static MysqlClusterMode findByValue(int value) {
        for (MysqlClusterMode e : MysqlClusterMode.values()) {
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
