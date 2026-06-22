# uw-mydb-proxy

基于 Netty 4.1 实现的 MySQL 分库分表代理中间件。对客户端而言它是一个标准的 MySQL Server（兼容 MySQL 协议），对后端而言它是 MySQL Client；所有数据采用 NIO + zero-copy 流式透传，自身不做聚合/排序/事务。

uw-mydb-center 是其配套的运管中心，负责配置下发、运行统计、告警与备份。

---

## 架构概览

```
                     ┌─────────────────────────────────────────────┐
   MySQL 客户端 ──►  │  uw-mydb-proxy (MySQL Server 侧)             │
   (JDBC / CLI)      │  ┌─────────────┐  ┌──────────────────────┐  │
                     │  │ ProxyServer │─►│ ProxyDataHandler      │  │
                     │  │ (Netty)     │  │  ├─ ProxySession      │  │
                     │  │             │  │  └─ SqlParser (路由)   │  │
                     │  │             │  └─ ProxyMultiNodeHandler│  │
                     │  │             │  ┌──────────────────────┐│  │
                     │  │             │  │ MySqlClient (Client侧)││  │
                     │  │             │  │  └─ MySqlPool (连接池)││  │
                     │  └─────────────┘  └──────────────────────┘│  │
                     └────────────────┬────────────────────────────┘
                                      │ RPC (配置/统计上报)
                                      ▼
                     ┌──────────────────────────────┐
                     │  uw-mydb-center (运管中心)    │
                     │  ┌─ 配置管理 (FusionCache)    │
                     │  ├─ 数据统计 / 慢SQL / 错误SQL │
                     │  ├─ 告警 (钉钉)               │
                     │  └─ 备份 (CLONE + binlog)     │
                     └──────────────────────────────┘
                                      │
                                      ▼
                     ┌──────────────────────────────┐
                     │  MySQL 集群 (主从/分库分表)   │
                     └──────────────────────────────┘
```

核心数据流：
1. 客户端连接 proxy，完成 MySQL 握手鉴权（支持 `mysql_native_password` 与 `caching_sha2_password`，后者含 full-auth RSA）。
2. 客户端发送 SQL，`SqlParser` 解析出表名与分片键，经 `RouteManager` 计算出目标节点集合。
3. 单节点路由：取一条后端连接直接转发；多节点路由：由 `ProxyMultiNodeHandler` 聚合各节点结果集后回写客户端。
4. 运行统计（QPS、慢 SQL、错误 SQL）异步上报到 center。

---

## 项目特色

- **协议级透传**：基于 Netty `ByteBuf` 的 zero-copy 流式转发，不做结果集聚合/排序，转发效率高。
- **多级路由链**：支持算法链组合，例如先按 SaaS 分库、再按日期分表，最多 3 级。
- **高性能连接池**：基于 `acquiredPermits` CAS 配额的弹性连接池，双队列（idle LIFO / busy）模型，支持空闲/忙时/寿命三类超时清理与健康检测。
- **动态建表**：分表不存在时由 proxy 自动 RPC 通知 center 创建（`RouteTableByAutoDate` / `RouteTableByAutoKey`）。
- **Hint 强制路由**：支持在 SQL 注释中强制指定路由节点或主从偏好。

---

## 路由算法

`RouteAlgorithm` 是抽象基类，子类通过 `RouteConfig.routeAlgorithm`（类全名）反射实例化，多个算法按 `parentId` 串成算法链。

### 分库算法

| 算法类 | 说明 | 配置参数（routeParamMap） |
|--------|------|--------------------------|
| `RouteDatabaseByPreset` | 按 value 精确匹配预设的 DataNode 映射 | `key=value` 形式，value 为 `clusterId.database` |
| `RouteDatabaseBySaas` | 按 SaaS ID 走 center 的 saas 节点分配（动态建库） | 无（映射由 center 管理） |

### 分表算法

| 算法类 | 说明 | 配置参数 |
|--------|------|----------|
| `RouteTableByMod` | 对 long 值取模分表（用 `Math.floorMod` 正确处理负数与 `Long.MIN_VALUE`） | `routeList=clusterId.database.table,...`（表数即模数） |
| `RouteTableByHash` | 一致性哈希分表（murmur3_32，128 虚拟节点） | `routeList=clusterId.database.table,...` |
| `RouteTableByPreset` | 按 value 精确匹配预设的表映射（覆盖 clusterId/database/table） | `key=clusterId.database.table` |
| `RouteTableByAutoDate` | 按日期自动分表，支持动态建表，表名后缀为日期格式化结果 | `baseNode=clusterId.database`、`datePattern`、`formatPattern`（白名单 9 种：`yyyy`/`yyyyMM`/`yyyyMMdd`/`yy`/`yyMM`/`yyMMdd`/`MM`/`MMdd`/`dd`）、`prepareNum` |
| `RouteTableByAutoKey` | 按任意 key 作表名后缀，配合动态建表 | `baseNode=clusterId.database` |

### 匹配模式（`matchType`）

当 SQL 未携带分片键时的行为：
- `MATCH_FIX`（0）：精确匹配，未命中分片键则报错。
- `MATCH_DEFAULT`（1）：走算法的默认路由（如日期算法兜底到"今天"）。
- `MATCH_ALL`（2）：广播到所有分片。

---

## SQL 支持

### DML
- `SELECT` / `UPDATE` / `DELETE` / `INSERT`（单 value，多 value 暂不支持）
- 支持 `WHERE col = ?` / `BETWEEN` / `IN (...)` / `>=` `<=` 分片键提取

### DDL
- `ALTER TABLE` / `CREATE TABLE` / `DROP TABLE` / `TRUNCATE TABLE` 等，按路由结果在对应分片执行

### 虚拟 Schema 上的透传命令
- `SET` / `SHOW` / `EXPLAIN` / `DESCRIBE`：直接转发到 base node

---

## Hint 语法

通过 SQL 注释强制指定路由，前缀为 `#mydb:`：

```sql
/*#mydb:route=* */
SELECT ... -- 强制广播到所有分片执行

/*#mydb:route=clusterId.database.table,clusterId.database.table */
SELECT ... -- 强制指定路由节点列表

/*#mydb:db-type=master */
SELECT ... -- 强制走主库（主从同步场景）
```

Hint 参数：
- `route`：路由节点。`*` 表示全部；或逗号分隔的 `clusterId.database` / `clusterId.database.table` 列表。
- `db-type`：主从偏好。`master`（强制主库）/ `slave` / `balance`（默认，读走从库、写走主库）。

---

## 连接池参数

后端 MySQL 连接池（`MySqlPool`）每个 `MysqlServerConfig` 独立维护，参数来自 center 下发的 `MysqlServerConfig`：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `connMin` | 1 | 最小连接数，housekeeping 清理时不低于此值 |
| `connMax` | 1000 | 最大连接数，CAS 配额守护 |
| `connIdleTimeout` | 600s | 空闲超时，超时且总数 > connMin 时关闭 |
| `connBusyTimeout` | 1800s | 忙时超时，busySet 中超时强制关闭 |
| `connMaxAge` | 3600s | 连接最大寿命，到期重建 |

前端连接：默认监听端口 `3300`，开启 `SO_KEEPALIVE` + `TCP_NODELAY`，2 小时读空闲检测半开连接。

---

## 慢 SQL 与错误 SQL

- 慢 SQL 阈值：`slowQueryMillis`，默认 10000ms，超过阈值的 SQL 异步上报到 center。
- 错误 SQL：后端返回 ERROR 包时自动捕获并上报。

---

## 部署配置

`bootstrap.yml` 关键配置：

```yaml
mydb:
  proxy:
    config-key: MYDB              # 与 center 侧 mydb_config_proxy.config_key 对应
    proxy-host: 0.0.0.0           # 监听地址
    proxy-port: 3300              # 监听端口
    mydb-center-host: http://uw-mydb-center  # center RPC 地址
    slow-query-millis: 10000      # 慢 SQL 阈值
```

Nacos / 环境变量：proxy 启动时通过 `configKey` 向 center 拉取完整配置（含 MySQL 集群信息、路由算法、分片规则），本地仅保留 `configKey` 与 center 地址。

---

## 模块结构

```
uw.mydb.proxy
├── server/          # 前端 Netty Server（ProxyServer/ProxySession/ProxyDataHandler/ProxyMultiNodeHandler）
├── mysql/           # 后端 MySQL Client（MySqlClient/MySqlPool/MySqlSession + task）
├── route/           # 路由算法（RouteManager + algorithm/ 子包）
├── sqlparse/        # SQL 解析与改写（SqlParser + parser/ 词法分析）
├── protocol/        # MySQL 协议包编解码（codec/codec + packet）
├── conf/            # 配置服务（FusionCache + RPC 拉取）
├── stats/           # 运行统计上报
├── util/            # 工具类（ByteBuf/鉴权插件/一致性哈希/字符集）
└── common/          # 与 center 共享的配置/上报 DTO
```

---

## 不支持的功能

为保障稳定性与转发性能，以下功能**不在中间件层实现**：

- 结果集聚合、排序、分页（应由应用层处理）
- 分布式事务
- `LAST_INSERT_ID()`（集群场景无意义）
