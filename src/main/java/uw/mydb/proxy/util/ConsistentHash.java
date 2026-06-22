package uw.mydb.proxy.util;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 一致性哈希（Consistent Hashing）实现，泛型参数 T 为真实节点类型。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>哈希算法：使用 Guava {@link Hashing#murmur3_32()}，输出 32 位整数与 {@link #circle}（{@link TreeMap}）的 Integer key 类型对齐。
 *       注意：之前误用 {@code murmur3_128().asInt()} 会把 128 位 hash 截断为 32 位，导致虚拟节点碰撞、分布不均。</li>
 *   <li>虚拟节点：每个真实节点按 {@code node.toString() + "/" + i}（i=0..numberOfReplicas-1）生成 {@link #numberOfReplicas} 个虚拟节点放入环。</li>
 *   <li>环状查找：使用 {@link TreeMap#tailMap(Object)} 找到 &gt;= 当前 key 的第一个虚拟节点；若 tailMap 为空（key 超过最大值）则回绕到 {@code firstKey()}（wrap-around）。</li>
 *   <li>非线程安全：构造后只读场景下并发查询无问题；动态 add/remove 需外部同步。</li>
 * </ul>
 *
 * @param <T> 真实节点类型
 * @author axeon
 */
public class ConsistentHash<T> {
    /**
     * 每个真实节点对应的虚拟节点数（越多分布越均匀，但内存与查找开销也越大）。
     */
    private final int numberOfReplicas;

    /**
     * 哈希环：key=murmur3_32(虚拟节点名)，value=真实节点。使用 TreeMap 支持 tailMap 顺时针查找。
     */
    private final SortedMap<Integer, T> circle = new TreeMap<>();

    /**
     * 哈希算法，使用 murmur3_32，与 {@link #circle} 的 Integer key 类型对齐。
     * 注意：之前误用 murmur3_128().asInt() 会把 128 位 hash 截断为 32 位，导致虚拟节点碰撞、分布不均。
     */
    private final HashFunction hash = Hashing.murmur3_32();

    /**
     * 构造一致性哈希环，为每个节点生成 numberOfReplicas 个虚拟节点。
     *
     * @param numberOfReplicas 每个真实节点的虚拟节点数
     * @param nodes            真实节点集合
     */
    public ConsistentHash(int numberOfReplicas, Collection<T> nodes) {
        this.numberOfReplicas = numberOfReplicas;
        for (T node : nodes) {
            add(node);
        }
    }

    /**
     * 增加一个真实节点，自动派生 numberOfReplicas 个虚拟节点放入环。
     *
     * @param node 真实节点
     */
    private void add(T node) {
        for (int i = 0; i < this.numberOfReplicas; i++) {
            circle.put(this.hash.hashUnencodedChars(node.toString() + "/" + i).asInt(), node);
        }
    }

    /**
     * 删除一个真实节点，连带移除其全部虚拟节点。
     *
     * @param node 真实节点
     */
    public void remove(T node) {
        for (int i = 0; i < this.numberOfReplicas; i++) {
            circle.remove(this.hash.hashUnencodedChars(node.toString() + "/" + i).asInt());
        }
    }

    /**
     * 根据 key 顺时针查找应落在的真实节点。
     * <p>
     * 查找逻辑：计算 key 的 hash -> 在环上找 &gt;= 该 hash 的第一个虚拟节点（{@code tailMap.firstKey()}）；
     * 若 key 超过环最大值则回绕到 {@code firstKey()}（wrap-around）。
     *
     * @param key 路由 key（如 saasId / userId）
     * @return 命中的真实节点；环为空时返回 null
     */
    public T get(String key) {
        if (circle.isEmpty()) {
            return null;
        }
        int code = this.hash.hashUnencodedChars(key).asInt();
        if (!circle.containsKey(code)) {
            // 沿环的顺时针找到一个虚拟节点
            SortedMap<Integer, T> tailMap = circle.tailMap(code);
            code = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        // 返回该虚拟节点对应的真实机器节点的信息
        return circle.get(code);
    }

}
