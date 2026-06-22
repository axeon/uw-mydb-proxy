package uw.mydb.proxy.protocol.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.proxy.util.ByteBufUtils;

import java.util.List;

/**
 * MySQL 协议半包/粘包拆分解码器（{@link ByteToMessageDecoder}）。
 * <p>
 * MySQL 协议每包前 3 字节为 payload 长度（little endian，{@code readUB3}），第 4 字节为 packetId。
 * 本解码器按该结构对入站 ByteBuf 做切分：
 * <ol>
 *   <li>可读字节 &lt; {@link #packetHeaderSize}（4 字节）时返回等待更多数据。</li>
 *   <li>读出 payload 长度后，若可读字节不足 {@code payloadLen + 1}（+1 为已读取的 packetId 字节）则回退 readerIndex 等待下次（半包处理）。</li>
 *   <li>长度超过 {@link #maxPacketSize}（16MB）时抛 {@link IllegalArgumentException} 防护恶意大包。</li>
 *   <li>长度满足时用 {@code readRetainedSlice} 零拷贝切出一个包含完整包头 + payload 的 ByteBuf 加入 out。</li>
 * </ol>
 * <p>
 * 为性能考虑，不解析成对象，下游直接操作 ByteBuf（透传给前端 channel）。
 * <p>
 * 线程安全：Netty 的 {@code ByteToMessageDecoder} 保证同一 channel 的 decode 串行执行；每个 channel 拥有独立实例。
 *
 * @author axeon
 */
public class MysqlPacketDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MysqlPacketDecoder.class);

    /**
     * MySQL 包头长度：3 字节 payload 长度 + 1 字节 packetId。
     */
    private final int packetHeaderSize = 4;

    /**
     * 单包最大字节数（16MB），超出则视为异常/恶意包并抛出异常关闭连接。
     */
    private final int maxPacketSize = 16 * 1024 * 1024;

    /**
     * 解码一条完整 MySQL 包。
     *
     * @param ctx channel 上下文
     * @param in  累积缓冲区
     * @param out 解析出的对象输出列表（加入切好的 ByteBuf）
     * @throws Exception 解码异常（如包超限）
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 4 bytes:3 length + 1 packetId
        if (in.readableBytes() < packetHeaderSize) {
            return;
        }
        //此处必须要标记初始readerIndex
        in.markReaderIndex();
        //包长度
        int packetLength = ByteBufUtils.readUB3(in);
        // 过载保护
        if (packetLength > maxPacketSize) {
            throw new IllegalArgumentException("Packet size over the limit:" + maxPacketSize);
        }
        //包不全的情况，下次再读
        if (in.readableBytes() < packetLength + 1) {
            // 半包回溯
            in.resetReaderIndex();
            return;
        }
        in.resetReaderIndex();
        int readLength = packetLength + packetHeaderSize;
        // 尝试用zero copy。
        ByteBuf buf = in.readRetainedSlice(readLength);
        out.add(buf);
    }
}
