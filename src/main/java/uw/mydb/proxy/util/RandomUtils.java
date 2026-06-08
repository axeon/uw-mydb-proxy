package uw.mydb.proxy.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数工具。
 *
 * @author axeon
 */
public class RandomUtils {
    private static final byte[] BYTES = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'q', 'w', 'e', 'r', 't',
            'y', 'u', 'i', 'o', 'p', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'z', 'x', 'c', 'v', 'b', 'n', 'm',
            'Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', 'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'Z', 'X',
            'C', 'V', 'B', 'N', 'M'};

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