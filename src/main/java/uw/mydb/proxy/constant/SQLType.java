package uw.mydb.proxy.constant;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * SQL 类型枚举，用于统计分流与解析路由判定。
 * <p>
 * {@code @JsonFormat(shape = OBJECT)} 使其序列化为 {"value":1,"label":"SELECT"} 形式，便于前端展示。
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum SQLType {
    /**
     * 其它类型（DDL/管理类语句，如 SET/SHOW/EXPLAIN/USE 等），value=0。
     */
    OTHER( 0, "OTHER" ),

    /**
     * SELECT 查询，value=1。
     */
    SELECT( 1, "SELECT" ),

    /**
     * INSERT 插入，value=2。
     */
    INSERT( 2, "INSERT" ),

    /**
     * UPDATE 更新，value=3。
     */
    UPDATE( 3, "UPDATE" ),

    /**
     * DELETE 删除，value=4。
     */
    DELETE( 4, "DELETE" );


    /**
     * 枚举数值，用于序列化与数据库存储。
     */
    private int value;

    /**
     * 枚举显示名（大写关键字形式）。
     */
    private String label;

    SQLType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据 value 反查枚举。
     *
     * @param value 数值
     * @return 对应枚举，找不到返回 null
     */
    public static SQLType findByValue(int value) {
        for (SQLType e : SQLType.values()) {
            if (value == e.value) {
                return e;
            }
        }
        return null;
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

}
