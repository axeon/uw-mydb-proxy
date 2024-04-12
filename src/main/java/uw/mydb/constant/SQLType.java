package uw.mydb.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * sql类型。
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum SQLType {
    /**
     * OTHER
     */
    OTHER( 0, "OTHER" ),

    /**
     * SELECT
     */
    SELECT( 1, "SELECT" ),

    /**
     * INSERT。
     */
    INSERT( 2, "INSERT" ),

    /**
     * UPDATE
     */
    UPDATE( 3, "UPDATE" ),

    /**
     * DELETE
     */
    DELETE( 4, "DELETE" );


    /**
     * 参数值
     */
    private int value;

    /**
     * 参数信息。
     */
    private String label;

    SQLType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static SQLType findByValue(int value) {
        for (SQLType e : SQLType.values()) {
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
