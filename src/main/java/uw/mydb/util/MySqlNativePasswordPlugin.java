package uw.mydb.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;

/**
 * mysql 插件工具类
 */
public class MySqlNativePasswordPlugin {

    private static final Logger log = LoggerFactory.getLogger(MySqlNativePasswordPlugin.class);

    public static final String PROTOCOL_PLUGIN_NAME = "mysql_native_password";

    public static byte[] scramble411(String password, byte[] seed) {
        if (password == null || password.length() == 0) {
            log.warn("password is empty");
            return new byte[0];
        }
        try {
            return SecurityUtils.scramble411(password.getBytes(), seed);
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
