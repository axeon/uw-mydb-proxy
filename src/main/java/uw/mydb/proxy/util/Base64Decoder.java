package uw.mydb.proxy.util;

/**
 * 轻量级 Base64 解码器（不依赖 JDK {@code java.util.Base64}）。
 * <p>
 * 用于解析 caching_sha2_password 认证流程中服务端返回的 PEM 格式 RSA 公钥（去除 BEGIN/END 标记后的纯 Base64 部分）。
 * 通过 256 项 {@link #decoderMap} 查表将 ASCII 字符映射为 6 位 sestet，跳过非 base64 字符，支持 '=' padding。
 */
public class Base64Decoder {

    /**
     * ASCII 字符 -> 6 位值映射表。
     * -1 表示非 base64 字符（跳过）；-2 表示 padding（'='）。
     */
    private static byte[] decoderMap = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31,
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1};

    /**
     * 跳过非 base64 字符，返回下一个有效字符；到达末尾返回 '=' 作为 padding。
     *
     * @param in     输入字节
     * @param pos    可变位置包装（读取后自增）
     * @param maxPos 最大允许位置
     * @return 下一个有效 base64 字符或 '='
     */
    private static byte getNextValidByte(byte[] in, IntWrapper pos, int maxPos) {
        while (pos.value <= maxPos) {
            if (in[pos.value] >= 0 && decoderMap[in[pos.value]] >= 0) {
                return in[pos.value++];
            }
            pos.value++;
        }
        // padding if reached max position
        return '=';
    }

    /**
     * 解码 Base64 字节流。自动跳过非 base64 字符，正确处理 padding。
     *
     * @param in     输入字节
     * @param pos    起始偏移
     * @param length 待解码长度
     * @return 解码后的原始字节
     */
    public static byte[] decode(byte[] in, int pos, int length) {
        IntWrapper offset = new Base64Decoder.IntWrapper(pos);
        byte[] sestet = new byte[4];

        int outLen = (length * 3) / 4; // over-estimated if non-base64 characters present
        byte[] octet = new byte[outLen];
        int octetId = 0;

        int maxPos = offset.value + length - 1;
        while (offset.value <= maxPos) {
            sestet[0] = decoderMap[getNextValidByte(in, offset, maxPos)];
            sestet[1] = decoderMap[getNextValidByte(in, offset, maxPos)];
            sestet[2] = decoderMap[getNextValidByte(in, offset, maxPos)];
            sestet[3] = decoderMap[getNextValidByte(in, offset, maxPos)];

            if (sestet[1] != -2) {
                octet[octetId++] = (byte) ((sestet[0] << 2) | (sestet[1] >>> 4));
            }
            if (sestet[2] != -2) {
                octet[octetId++] = (byte) (((sestet[1] & 0xf) << 4) | (sestet[2] >>> 2));
            }
            if (sestet[3] != -2) {
                octet[octetId++] = (byte) (((sestet[2] & 3) << 6) | sestet[3]);
            }
        }
        // return real-length value
        byte[] out = new byte[octetId];
        System.arraycopy(octet, 0, out, 0, octetId);
        return out;
    }

    /**
     * 可变 int 包装器，用于在 {@link #getNextValidByte} 中以引用方式更新读取位置。
     */
    public static class IntWrapper {
        /**
         * 当前读取位置。
         */
        public int value;

        /**
         * @param value 初始位置
         */
        public IntWrapper(int value) {
            this.value = value;
        }
    }
}

