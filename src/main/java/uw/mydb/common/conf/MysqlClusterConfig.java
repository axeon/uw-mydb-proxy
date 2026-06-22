package uw.mydb.common.conf;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MySQL 集群配置实体（proxy 与 center 共享）。
 * <p>
 * 一个集群包含多个 {@link MysqlServerConfig}，按读写分离/权重做负载均衡：
 * <ul>
 *   <li>{@link #fetchServerConfig(boolean)} 按轮询（{@link AtomicInteger#getAndIncrement}）选取目标 server。</li>
 *   <li>主库（isMaster=true）从 {@link #serverMasterWeightList} 选；从库从 {@link #serverAllWeightList} 选。</li>
 *   <li>权重通过把同一 server 重复 N 次加入 list 实现（{@link #initServerWeightList}），shuffle 后顺序固定。</li>
 * </ul>
 * {@link #serverMasterWeightList}/{@link #serverAllWeightList} 标 {@code @JsonIgnore} 不参与序列化，仅 proxy 端使用。
 */
public class MysqlClusterConfig {

    /**
     * 集群 ID（唯一）。
     */
    private long id;

    /**
     * 集群名称（展示用）。
     */
    private String clusterName;

    /**
     * 复制组类型（如主从 / 组复制 / MGR，具体含义见 {@code MysqlClusterMode}）。
     */
    private int clusterType;

    /**
     * 故障切换类型（见 {@code MysqlClusterSwitchMode}）。
     */
    private int switchType;

    /**
     * 集群下的 MySQL 主机列表（含权重、连接池参数等）。
     */
    private List<MysqlServerConfig> serverList = new ArrayList<>();

    /**
     * 主库轮询位置（原子递增取模），仅写场景使用。{@code @JsonIgnore} 不序列化。
     */
    @JsonIgnore
    private final AtomicInteger serverMasterWeightPos = new AtomicInteger(0);

    /**
     * 全部 server 轮询位置（原子递增取模），读场景使用。{@code @JsonIgnore}。
     */
    @JsonIgnore
    private final AtomicInteger serverAllWeightPos = new AtomicInteger(0);

    /**
     * 主库权重展开后的列表（按 weight 重复 + shuffle），{@code @JsonIgnore}。volatile 保证可见性。
     */
    @JsonIgnore
    private volatile List<MysqlServerConfig> serverMasterWeightList;

    /**
     * 全部 server 权重展开后的列表（按 weight 重复 + shuffle），{@code @JsonIgnore}。volatile。
     */
    @JsonIgnore
    private volatile List<MysqlServerConfig> serverAllWeightList;


    /**
     * 默认构造器（反序列化用）。
     */
    public MysqlClusterConfig() {
    }

    /**
     * 全参构造器。
     *
     * @param id          集群 ID
     * @param clusterName 集群名
     * @param clusterType 复制组类型
     * @param switchType  切换类型
     * @param serverList  server 列表
     */
    public MysqlClusterConfig(long id, String clusterName, int clusterType, int switchType, List<MysqlServerConfig> serverList) {
        this.id = id;
        this.clusterName = clusterName;
        this.clusterType = clusterType;
        this.switchType = switchType;
        this.serverList = serverList;
    }

    /**
     * 按读写场景轮询选取一个目标 server。主库场景从 masterWeightList 选（仅写节点）；
     * 从库场景从 allWeightList 选（含读副本）。
     *
     * @param isMaster true 取主库，false 取从库
     * @return 选中的 server 配置
     */
    public MysqlServerConfig fetchServerConfig(boolean isMaster) {
        if (isMaster){
            return serverMasterWeightList.get( serverMasterWeightPos.getAndIncrement() % serverMasterWeightList.size() );
        }else {
            return serverAllWeightList.get( serverAllWeightPos.getAndIncrement() % serverAllWeightList.size() );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MysqlClusterConfig that = (MysqlClusterConfig) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    /**
     * 根据各 server 的 weight 构建主库 / 全部 server 的轮询列表（同一 server 重复 weight 次，shuffle 打散）。
     * <p>
     * synchronized 保证只初始化一次（双检锁语义），后续 fetchServerConfig 直接读 list 即可。
     */
    public synchronized void initServerWeightList() {
        if (serverMasterWeightList == null || serverAllWeightList == null) {
            List<MysqlServerConfig> masterList = new ArrayList<>();
            List<MysqlServerConfig> allList = new ArrayList<>();
            for (MysqlServerConfig config : serverList) {
                if (config.getWeight()<1){
                    config.setWeight( 1 );
                }
                for (int i = 0; i < config.getWeight(); i++) {
                    masterList.add( config );
                    allList.add( config );
                }
            }
            Collections.shuffle( masterList );
            Collections.shuffle( allList );
            this.serverMasterWeightList = masterList;
            this.serverAllWeightList = allList;
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public int getClusterType() {
        return clusterType;
    }

    public void setClusterType(int clusterType) {
        this.clusterType = clusterType;
    }

    public int getSwitchType() {
        return switchType;
    }

    public void setSwitchType(int switchType) {
        this.switchType = switchType;
    }

    public List<MysqlServerConfig> getServerList() {
        return serverList;
    }

    public void setServerList(List<MysqlServerConfig> serverList) {
        this.serverList = serverList;
    }

}
