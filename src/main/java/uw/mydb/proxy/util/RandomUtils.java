package uw.mydb.proxy.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机字节工具，全静态方法。基于 {@link ThreadLocalRandom} 生成指定长度的可见 ASCII 字符字节序列，
 * 主要用于 MySQL 握手认证种子生成（需要可打印字符以兼容老客户端）。
 *
 * @author axeon
 */
public class RandomUtils {
    /**
     * 候选字符表（数字 + 大小写字母，共 62 个），randomBytes 从中均匀采样。
     */
    private static final byte[] BYTES = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'q', 'w', 'e', 'r', 't',
            'y', 'u', 'i', 'o', 'p', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'z', 'x', 'c', 'v', 'b', 'n', 'm',
            'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'Z', 'X',
            'C', 'V', 'B', 'N', 'M'};

    /**
     * 生成指定长度的随机字节序列（字符集为数字 + 大小写字母）。
     *
     * @param size 字节长度
     * @return 随机字节数组
     */
    public static final byte[] randomBytes(int size) {
        byte[] bb = BYTES;
        byte[] ab = new byte[size];
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < size; i++) {
            ab[i] = bb[rng.nextInt(bb.length)];
        }
        return ab;
    }

}