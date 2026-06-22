package uw.mydb.proxy.util;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * MySQL 字符集与 collation 索引映射工具，全静态方法。
 * <p>
 * 维护 collationIndex &harr; charsetName 的双向映射，用于在 MySQL 协议握手与字段元数据中
 * 将 collation 索引（如 255=utf8mb4_general_ci）转换为 Java 字符集名称。
 */
public class CharsetUtils {
    public static final Logger logger = LoggerFactory.getLogger(CharsetUtils.class);

    /**
     * collationIndex -> charsetName 映射。一个 collationIndex 唯一对应一个 charset。
     */
    private static final Map<Integer, String> INDEX_TO_CHARSET = new HashMap<>();

    /**
     * charsetName（小写） -> 默认 collationIndex 映射。一个 charset 支持多个 collation，此处存默认值。
     */
    private static final Map<String, Integer> CHARSET_TO_INDEX = new HashMap<>();

    /**
     * collationName -> {@link CharsetCollation} 对象映射（当前未使用，保留）。
     */
    @SuppressWarnings("unused")
    private static final Map<String, CharsetCollation> COLLATION_TO_CHARSETCOLLATION = new HashMap<>();

    /**
     * 根据 collationIndex 获取 Java charset 名称。找不到时返回 "UTF-8" 并记录 WARN。
     *
     * @param index collation 索引（1~255+）
     * @return Java charset 名（如 "UTF-8"、"GBK"）
     */
    public static final String getCharset(int index) {
        String charset = INDEX_TO_CHARSET.get(index);
        if (charset == null) {
            //System.out.println("warning charset is null ,not loaded from server ,please fix it !!");
            logger.warn("charset is null ,not loaded from server ,please fix it !!");
            return "UTF-8";
        }
        return charset;
    }

    /**
     * 因为 每一个 charset 对应多个 collationIndex, 所以这里返回的是默认的那个 collationIndex；
     * 如果想获取确定的值 index，而非默认的index, 那么需要使用 getIndexByCollationName 或者
     * getIndexByCharsetNameAndCollationName
     *
     * @param charset
     * @return
     */
    public static final int getIndex(String charset) {
        if (Strings.isNullOrEmpty(charset)) {
            return 0;
        } else {
            Integer i = CHARSET_TO_INDEX.get(charset.toLowerCase());
            if (i == null && "Cp1252".equalsIgnoreCase(charset)) {
                charset = "latin1";
            }
            i = CHARSET_TO_INDEX.get(charset.toLowerCase());
            return (i == null) ? 0 : i;
        }
    }

}

/**
 * 该类用来表示 mysqld 数据库中 字符集、字符集支持的collation、字符集的collation的index、 字符集的默认collation
 * 的对应关系： 一个字符集一般对应(支持)多个collation，其中一个是默认的 collation，每一个 collation对应一个唯一的index,
 * collationName 和 collationIndex 一一对应，
 * 每一个collationIndex对应到一个字符集，不同的collationIndex可以对应到相同的字符集， 所以字符集 到
 * collationIndex 的对应不是唯一的，一个字符集对应多个 index(有一个默认的 collation的index)， 而
 * collationIndex 到 字符集 的对应是确定的，唯一的； mysqld 用 collation 的 index 来描述排序规则。
 *
 * @author Administrator
 */
class CharsetCollation {
    // mysqld支持的字符编码名称，注意这里不是java中的unicode编码的名字，
    // 二者之间的区别和联系可以参考驱动jar包中的com.mysql.jdbc.CharsetMapping源码
    private String charsetName;
    private int collationIndex; // collation的索引顺序
    private String collationName; // collation 名称
    private boolean isDefaultCollation = false; // 该collation是否是字符集的默认collation

    public CharsetCollation(String charsetName, int collationIndex, String collationName, boolean isDefaultCollation) {
        this.charsetName = charsetName;
        this.collationIndex = collationIndex;
        this.collationName = collationName;
        this.isDefaultCollation = isDefaultCollation;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    public int getCollationIndex() {
        return collationIndex;
    }

    public void setCollationIndex(int collationIndex) {
        this.collationIndex = collationIndex;
    }

    public String getCollationName() {
        return collationName;
    }

    public void setCollationName(String collationName) {
        this.collationName = collationName;
    }

    public boolean isDefaultCollation() {
        return isDefaultCollation;
    }

    public void setDefaultCollation(boolean isDefaultCollation) {
        this.isDefaultCollation = isDefaultCollation;
    }
}
