package uw.mydb.proxy.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

/**
 * MySQL mysql_native_password 认证插件工具（MySQL 4.1+ 默认插件，基于 SHA-1）。
 * 提供 scramble 生成、握手种子构造等静态方法。
 */
public class MySqlNativePasswordPlugin {

    private static final Logger log = LoggerFactory.getLogger(MySqlNativePasswordPlugin.class);

    /**
     * 插件协议名，与 MySQL 服务端 mysql_native_password 对齐。
     */
    public static final String PROTOCOL_PLUGIN_NAME = "mysql_native_password";

    /**
     * 计算 mysql_native_password 的 scramble（委托 {@link SecurityUtils#scramble411}）。
     * 空密码返回空字节数组，SHA-1 不可用时记录 WARN 并返回 null。
     *
     * @param password 明文密码
     * @param seed     服务端种子（20 字节）
     * @return scramble 字节（20 字节），失败返回 null
     */
    public static byte[] scramble411(String password, byte[] seed) {
        if (password == null || password.length() == 0) {
            log.warn("password is empty");
            return new byte[0];
        }
        try {
            return SecurityUtils.scramble411(password.getBytes(StandardCharsets.UTF_8), seed);
        } catch (NoSuchAlgorithmException e) {
            log.warn("no such algorithm", e);
            return null;
        }
    }

    public static byte[] scramble411(String password, String seed) {
        return scramble411(password, seed.getBytes());
    }

    public static byte[][] nextSeedBuild() {
        byte[] authPluginDataPartOne = RandomUtils.randomBytes(8);
        byte[] authPluginDataPartTwo = RandomUtils.randomBytes(12);

        // 保存认证数据
        byte[] seed = new byte[20];
        System.arraycopy(authPluginDataPartOne, 0, seed, 0, 8);
        System.arraycopy(authPluginDataPartTwo, 0, seed, 8, 12);

        byte[][] result = new byte[3][1];
        result[0] = authPluginDataPartOne;
        result[1] = authPluginDataPartTwo;
        result[2] = seed;
        return result;
    }

    public static String[] nextSeedStringBuild() {
        byte[][] bytes = nextSeedBuild();
        return new String[]{
                new String(bytes[0]),
                new String(bytes[1]),
                new String(bytes[2]),
        };
    }
}
