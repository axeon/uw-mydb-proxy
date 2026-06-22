package uw.mydb.proxy.util;


import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * MySQL 协议专用的 ByteBuf 编解码工具，全静态方法。
 * <p>
 * 涵盖：
 * <ul>
 *   <li>定点小整数 little-endian 读取与写入：{@link #readUB2}/{@link #writeUB2}（2 字节无符号）、
 *       {@link #readUB3}/{@link #writeUB3}（3 字节，用于包头长度）、{@link #readUB4}/{@link #writeUB4}（4 字节）。</li>
 *   <li>{@link #readLong}/{@link #writeLong}：8 字节 little-endian。</li>
 *   <li>MySQL Length-Coded Integer（LenEnc）：{@link #readLenEncInt}/{@link #writeLenEncInt}。
 *       首字节 &lt;251 直接表示；252/253/254 分别跟 2/3/8 字节小端整数；251 表示 NULL。</li>
 *   <li>字符串读写：
 *       <ul>
 *         <li>{@code *WithNull}：以 0x00 终止的字符串/字节（需扫描 terminator）。</li>
 *         <li>{@code *WithLenEnc}：以 LenEnc 长度前缀的字符串/字节。</li>
 *         <li>{@code *WithEof}：读取到 buf 末尾（剩余全部）。</li>
 *       </ul>
 *   </li>
 *   <li>{@link #calcLenEncLength}/{@link #calcLenEncDataLength}：预计算编码后字节数，用于分配缓冲。</li>
 * </ul>
 * 所有方法均为单线程语义下的纯函数（不持有状态），可在任意线程调用。
 *
 * @author axeon
 */
public class ByteBufUtils {

    /**
     * 空字节数组常量，用于 NULL 或空值场景的统一引用。
     */
    public static final byte[] NULL_DATA = new byte[0];

    /**
     * 读取 2 字节 little-endian 无符号整数（0 ~ 65535）。
     *
     * @param buf 源 ByteBuf
     * @return 无符号 16 位整数
     */
    public static int readUB2(ByteBuf buf) {
        int i = buf.readByte() & 0xff;
        i |= (buf.readByte() & 0xff) << 8;
        return i;
    }

    /**
     * 读取 3 字节 little-endian 无符号整数（0 ~ 16777215），主要用于 MySQL 包头长度。
     *
     * @param buf 源 ByteBuf
     * @return 无符号 24 位整数
     */
    public static int readUB3(ByteBuf buf) {
        int i = buf.readByte() & 0xff;
        i |= (buf.readByte() & 0xff) << 8;
        i |= (buf.readByte() & 0xff) << 16;
        return i;
    }

    /**
     * 读取 4 字节 little-endian 无符号整数（0 ~ 4294967295）。
     *
     * @param buf 源 ByteBuf
     * @return 无符号 32 位整数（long 容器避免符号扩展）
     */
    public static long readUB4(ByteBuf buf) {
        long l = buf.readByte() & 0xff;
        l |= (buf.readByte() & 0xff) << 8;
        l |= (buf.readByte() & 0xff) << 16;
        l |= (buf.readByte() & 0xff) << 24;
        return l;
    }

    /**
     * 读取 8 字节 little-endian long（LenEnc 首字节 0xFE 时使用）。
     *
     * @param buf 源 ByteBuf
     * @return 64 位整数
     */
    public static long readLong(ByteBuf buf) {
        long l = (long) (buf.readByte() & 0xff);
        l |= (long) (buf.readByte() & 0xff) << 8;
        l |= (long) (buf.readByte() & 0xff) << 16;
        l |= (long) (buf.readByte() & 0xff) << 24;
        l |= (long) (buf.readByte() & 0xff) << 32;
        l |= (long) (buf.readByte() & 0xff) << 40;
        l |= (long) (buf.readByte() & 0xff) << 48;
        l |= (long) (buf.readByte() & 0xff) << 56;
        return l;
    }

    /**
     * 读取 MySQL LenEnc 整数。首字节 251 表示 NULL（返回 -1）；252/253/254 分别后跟 2/3/8 字节小端数；否则首字节即数值。
     *
     * @param buf 源 ByteBuf
     * @return 数值；NULL 时返回 -1
     */
    public static long readLenEncInt(ByteBuf buf) {
        int length = buf.readByte() & 0xff;
        switch (length) {
            case 251:
                return -1L;
            case 252:
                return readUB2(buf);
            case 253:
                return readUB3(buf);
            case 254:
                return readLong(buf);
            default:
                return length;
        }
    }

    /**
     * 写入 2 字节 little-endian 无符号整数。
     *
     * @param buf 目标 ByteBuf
     * @param i   待写入数值（仅取低 16 位）
     */
    public static final void writeUB2(ByteBuf buf, int i) {
        buf.writeByte((byte) (i & 0xff));
        buf.writeByte((byte) (i >>> 8));
    }

    /**
     * 写入 3 字节 little-endian 无符号整数（用于 MySQL 包头长度）。
     *
     * @param buf 目标 ByteBuf
     * @param i   待写入数值（仅取低 24 位）
     */
    public static final void writeUB3(ByteBuf buf, int i) {
        buf.writeByte((byte) (i & 0xff));
        buf.writeByte((byte) (i >>> 8));
        buf.writeByte((byte) (i >>> 16));
    }

    /**
     * 写入 4 字节 little-endian int。
     *
     * @param buf 目标 ByteBuf
     * @param i   待写入数值
     */
    public static final void writeInt(ByteBuf buf, int i) {
        buf.writeByte((byte) (i & 0xff));
        buf.writeByte((byte) (i >>> 8));
        buf.writeByte((byte) (i >>> 16));
        buf.writeByte((byte) (i >>> 24));
    }

    /**
     * 写入 4 字节 IEEE 754 float（基于 {@link #writeInt}）。
     *
     * @param buf 目标 ByteBuf
     * @param f   待写入浮点数
     */
    public static final void writeFloat(ByteBuf buf, float f) {
        writeInt(buf, Float.floatToIntBits(f));
    }

    /**
     * 写入 4 字节 little-endian 无符号整数。
     *
     * @param buf 目标 ByteBuf
     * @param l   待写入数值（仅取低 32 位）
     */
    public static final void writeUB4(ByteBuf buf, long l) {
        buf.writeByte((byte) (l & 0xff));
        buf.writeByte((byte) (l >>> 8));
        buf.writeByte((byte) (l >>> 16));
        buf.writeByte((byte) (l >>> 24));
    }

    /**
     * 写入 8 字节 little-endian long。
     *
     * @param buf 目标 ByteBuf
     * @param l   待写入数值
     */
    public static final void writeLong(ByteBuf buf, long l) {
        buf.writeByte((byte) (l & 0xff));
        buf.writeByte((byte) (l >>> 8));
        buf.writeByte((byte) (l >>> 16));
        buf.writeByte((byte) (l >>> 24));
        buf.writeByte((byte) (l >>> 32));
        buf.writeByte((byte) (l >>> 40));
        buf.writeByte((byte) (l >>> 48));
        buf.writeByte((byte) (l >>> 56));
    }

    /**
     * 写入 8 字节 IEEE 754 double（基于 {@link #writeLong}）。
     *
     * @param buf 目标 ByteBuf
     * @param d   待写入浮点数
     */
    public static final void writeDouble(ByteBuf buf, double d) {
        writeLong(buf, Double.doubleToLongBits(d));
    }

    /**
     * 只写入LenEnc格式。
     *
     * @param buf
     * @param value
     */
    public static final void writeLenEncInt(ByteBuf buf, long value) {
        if (value < 251) {
            buf.writeByte((byte) value);
        } else if (value < 0x10000L) {
            buf.writeByte((byte) 252);
            writeUB2(buf, (int) value);
        } else if (value < 0x1000000L) {
            buf.writeByte((byte) 253);
            writeUB3(buf, (int) value);
        } else {
            buf.writeByte((byte) 254);
            writeLong(buf, value);
        }
    }

    /**
     * 写入以0x00结束的字符串。
     *
     * @param buf
     * @return
     */
    public static final void writeStringWithNull(ByteBuf buf, String data) {
        writeBytesWithNull(buf, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写入以0x00结束的字符串。
     *
     * @param buf
     * @return
     */
    public static final void writeStringWithLenEnc(ByteBuf buf, String data) {
        writeBytesWithLenEnc(buf, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 以0x00结尾写入Bytes。
     *
     * @param buf
     * @param data
     */
    public static final void writeBytesWithNull(ByteBuf buf, byte[] data) {
        if (data != null) {
            buf.writeBytes(data);
        }
        buf.writeByte((byte) 0);
    }

    /**
     * 按照LenCode格式写入数据。
     *
     * @param buf
     * @param data
     */
    public static final void writeBytesWithLenEnc(ByteBuf buf, byte[] data) {
        if (data == null) {
            data = NULL_DATA;
        }
        int length = data.length;
        if (length < 251) {
            buf.writeByte((byte) length);
        } else if (length < 0x10000L) {
            buf.writeByte((byte) 252);
            writeUB2(buf, length);
        } else if (length < 0x1000000L) {
            buf.writeByte((byte) 253);
            writeUB3(buf, length);
        } else {
            buf.writeByte((byte) 254);
            writeLong(buf, length);
        }
        buf.writeBytes(data);
    }


    /**
     * 计算lenEnc格式的长度。
     *
     * @param value
     * @return
     */
    public static final int calcLenEncLength(long value) {
        if (value < 251) {
            return 1;
        } else if (value < 0x10000L) {
            return 3;
        } else if (value < 0x1000000L) {
            return 4;
        } else {
            return 9;
        }
    }

    /**
     * 计算LenEnc连带数据的长度。
     *
     * @param data
     * @return
     */
    public static final int calcLenEncDataLength(byte[] data) {
        int length = data.length;
        if (length < 251) {
            return 1 + length;
        } else if (length < 0x10000L) {
            return 3 + length;
        } else if (length < 0x1000000L) {
            return 4 + length;
        } else {
            return 9 + length;
        }
    }

    /**
     * 读取以0x00结束的字符串。
     *
     * @param buf
     * @return
     */
    public static String readStringWithEof(ByteBuf buf) {
        return new String(readBytesWithEof(buf), StandardCharsets.UTF_8);
    }


    /**
     * 读取以0x00结束的字符串。
     *
     * @param buf
     * @return
     */
    public static String readStringWithNull(ByteBuf buf) {
        return new String(readBytesWithNull(buf), StandardCharsets.UTF_8);
    }

    /**
     * 读取以0x00结束的字符串。
     *
     * @param buf
     * @return
     */
    public static String readStringWithLenEnc(ByteBuf buf) {
        return new String(readBytesWithLenEnc(buf), StandardCharsets.UTF_8);
    }


    /**
     * 读取到EOF的数据。
     *
     * @param buf
     * @return
     */
    public static byte[] readBytesWithEof(ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    /**
     * 读取LenEnc格式的Bytes。
     *
     * @param buf
     * @return
     */
    public static byte[] readBytesWithLenEnc(ByteBuf buf) {
        int length = (int) readLenEncInt(buf);
        if (length <= 0) {
            return NULL_DATA;
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
    }


    /**
     * 读取以0x00结尾的Bytes。
     *
     * @param buf
     * @return
     */
    public static byte[] readBytesWithNull(ByteBuf buf) {
        int start = buf.readerIndex();
        int nullIndex = -1;
        for (int i = start; i < buf.writerIndex(); i++) {
            if (buf.getByte(i) == 0x00) {
                nullIndex = i;
                break;
            }
        }
        if (nullIndex < 0) {
            // 未找到null终止符，读取剩余所有数据
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        }
        int len = nullIndex - start;
        if (len == 0) {
            buf.skipBytes(1);
            return NULL_DATA;
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        buf.skipBytes(1);
        return data;
    }

}
