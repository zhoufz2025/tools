# Redis 在项目工程中的使用分析

## 一、概述

本项目（综合理财管理平台 V6.0）基于 **Jedis** 客户端使用 Redis，通过统一的 **JedisCacheBase** 封装各类缓存操作，支持单机、哨兵、集群三种部署模式，并具备主备双写（双机房同步）、发布订阅、分布式锁等能力。依赖管理在 `lcpt-dependencies` 中统一维护，各业务模块通过 `lcpt-base-core` 中的缓存基础设施使用 Redis。

---

## 二、技术栈与依赖

### 2.1 核心依赖

| 依赖 | 说明 | 所在模块 |
|------|------|----------|
| `redis.clients:jedis` | Redis Java 客户端 | lcpt-base-core、lcpt-boot-dependencies* |
| `spring-boot-starter-data-redis` | Spring Data Redis（可选） | lcpt-boot-dependencies* |
| `spring-session-data-redis` | Spring Session 存 Redis（可选） | lcpt-boot-dependencies |

Redis 实际访问均通过 **Jedis** 及项目封装的 `JedisCacheBase`、`JedisFactory` 完成，未直接使用 `RedisTemplate`/Lettuce。

### 2.2 核心模块

- **lcpt-base-core**：Redis 连接与操作封装  
  - `JedisConfig`：Redis 配置（单机/哨兵/集群、连接池、前缀、database 等）  
  - `JedisFactory`：主 Redis 连接池/集群/哨兵管理  
  - `JedisSynConfig` / `JedisSynFactory`：双写场景下“备机房”Redis 连接  
  - `JedisCacheBase`：统一 Redis 操作 API（String/Hash/Set/List/事务/发布订阅等）  
  - `JedisSynCacheBase`：主写成功后同步写备机房  
  - `SentinelMasterMonitor`：哨兵模式下 Master 心跳与重连  

- **lcpt-pub-cache**：公共业务缓存与工具  
  - 各类 *Cache（见下文“业务缓存分类”）  
  - `RedisLockUtil`：分布式锁  
  - `JedisMessageUtils`：基于 Redis 的发布订阅与缓存刷新通知  

- **lcpt-web-bizframe-core**：Web 框架层  
  - 菜单/交易/权限等缓存、登录态、Redis 键查询、订阅监听等  

- **lcpt-dxfund-pub-cache / lcpt-dxtrust-pub-cache / lcpt-dxasset-pub-cache**：基金/信托/资产等业务缓存与产品索引等  

---

## 三、配置说明

### 3.1 配置项来源

配置集中在各模块的 `application.properties`（或 see/template 下的模板）中，通过 `redis.*` 前缀注入到 `JedisConfig`。

### 3.2 主 Redis 配置项（redis.*）

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `redis.enabled` | 是否启用 Redis | true |
| `redis.ip` | 单机模式 IP | 127.0.0.1 / 10.20.26.47 |
| `redis.port` | 单机模式端口 | 6379 / 6381 |
| `redis.password` | 密码（支持密文/解密） | 空或密文 |
| `redis.maxTotal` | 连接池最大连接数 | 100 |
| `redis.maxIdle` | 最大空闲连接 | 10 |
| `redis.maxWaitMillis` | 获取连接最大等待时间(ms) | 100000 |
| `redis.testOnBorrow` | 借用时是否检测 | true |
| `redis.timeout` | 读写超时(ms)，默认 2000 | 2000 |
| `redis.isSentinel` | 是否哨兵模式 | false |
| `redis.masterName` | 哨兵 Master 名称 | 1 |
| `redis.sentinelEndpoint` | 哨兵地址列表 | ip1:port1,ip2:port2,ip3:port3 |
| `redis.sentinelPassword` | 哨兵密码（可选） | - |
| `redis.isCluster` | 是否集群模式 | false |
| `redis.clusterEndpoint` | 集群节点列表 | ip1:port1,ip2:port2,ip3:port3 |
| `redis.prefix` | Key 前缀（可选） | - |
| `redis.database` | 使用的 DB 索引，默认 0 | 0 |
| `redis.caches` | 需刷新的缓存 Bean 列表（逗号分隔） | 如 DxTrustTaInfoCache,SysArgCache |

启用条件：`@ConditionalOnProperty(value="redis.enabled", havingValue="true")`，只有 `redis.enabled=true` 时才会创建 `JedisConfig` 和 Redis 连接。

### 3.3 双写（备机房）配置（redis.syn.*）

当存在主备双机房同步时，会启用 `JedisSynConfig` / `JedisSynFactory`，配置项与主 Redis 类似，前缀为 `redis.syn.*`，例如：

- `redis.syn.enabled`、`redis.syn.ip`、`redis.syn.port`、`redis.syn.password`
- `redis.syn.isSentinel`、`redis.syn.masterName`、`redis.syn.sentinelEndpoint`、`redis.syn.sentinelPassword`
- `redis.syn.isCluster`、`redis.syn.clusterEndpoint`
- `redis.syn.throwException`：备机房写失败时是否抛异常（默认 false）

双写逻辑：主 Redis 写成功后，再对 Syn 端执行相同写操作；失败时打日志，并按 `throwException` 决定是否抛出。

### 3.4 消息广播（缓存刷新通知）

- `lcpt.redis.broadcast.enable`：是否启用“消息中心”式缓存同步（发布订阅），默认 false。  
- 启用后通过固定 channel `lcpt-cache-message-channel` 发布/订阅缓存刷新消息，实现多实例间本地缓存刷新。

---

## 四、架构与使用模式

### 4.1 连接与部署模式

- **单机**：`redis.ip` + `redis.port`，使用 `JedisPool`。  
- **哨兵**：`redis.isSentinel=true`，配置 `masterName`、`sentinelEndpoint`（及可选 `sentinelPassword`），使用 `JedisSentinelPool`，并由 `SentinelMasterMonitor` 做心跳与重连。  
- **集群**：`redis.isCluster=true`，配置 `clusterEndpoint`，使用 `JedisCluster`。  

所有模式均通过 `JedisFactory.getInstance()` 获取连接；集群时用 `getJedisCluster()`，非集群用 `getJedis()`，用完后需 `returnResource(jedis)`。

### 4.2 统一操作入口：JedisCacheBase

业务代码不直接使用 Jedis/JedisCluster，而是调用 `JedisCacheBase` 的静态方法，内部自动选择集群或单机/哨兵，并支持：

- **Key 前缀**：若配置了 `redis.prefix`，所有 key 会加上该前缀。  
- **事务**：部分写操作使用 `WATCH/MULTI/EXEC` 保证原子性，失败会重试（如 `retryCount=50`）。  
- **双写**：写操作成功后，若 Syn 已启用且当前线程未关闭双写，则同步调用 `JedisSynCacheBase` 对备机房执行相同操作。

主要 API 类型：

- **String**：`get`、`set`、`clear`、`expire`、`exists` 等  
- **Hash**：`hget`、`hset`、`hmget`、`hmset`、`hgetAll`、`hkeys`、`hdel`、`hexists`、`hmDelAndAdd` 等  
- **Set**：`sadd`、`srem`、`smember`、`scard`、`saddall` 等  
- **List**：列表相关操作  
- **事务**：`doInTransaction`、`doWithOutTransaction`（内部封装 JedisAction）  
- **发布订阅**：`publishMessage(channel, message)`、`subscribe(pubSub, channels...)`  

业务侧大量使用 Hash 存“缓存名 -> 字段 -> JSON”的结构（如菜单、交易、产品信息等）。

### 4.3 双写与同步

- 主写：`JedisCacheBase` 先写主 Redis（单机/哨兵/集群）。  
- 备写：若 `JedisSynFactory.getInstance()!=null` 且当前线程未关闭双写（`JedisCacheBase.isSyn()`），则再调用 `JedisSynCacheBase` 的对应方法写备机房。  
- 顺序：先主后备；备写失败仅打日志，可选抛异常（`redis.syn.throwException`）。  

通过 `JedisCacheBase.setSyn(false)` 可在当前线程关闭双写，避免特定场景写备机房。

---

## 五、业务使用场景分类

### 5.1 分布式锁

- **位置**：`lcpt-pub-cache` → `RedisLockUtil`  
- **实现**：Key 前缀 `FINA_REDIS_LOCK:` + 业务 key，使用 `SET key value NX EX seconds`（Jedis `SetParams.nx().ex(seconds)`）实现简单互斥锁。  
- **方法**：`lock(key, value, seconds)` / `lock(key, seconds)`、`unlock(key)`。  
- **说明**：集群与单机/哨兵均支持；unlock 失败会重试 3 次并打日志。

### 5.2 会话与登录态

- **位置**：`lcpt-web-bizframe-core`（如 `SignController`、登录相关）  
- **用途**：  
  - 操作员登录态：`JedisCacheBase.set(operator_code, serialize(userInfo))`  
  - 验证码/会话：如 `validateSessionId` + 过期时间（分钟级）  
  - 校验、清理会话等：`get`、`clear`、`expire`  

数据多为序列化对象或字符串，带 TTL，用于单点登录/会话校验。

### 5.3 权限与菜单缓存（Web 框架）

- **BizFrameMenuCache / BizFrameTransCache / BizMenuConditionCache 等**  
- **存储结构**：Redis Hash，如 `MENUS_CACHE_MAP`、`TRANSMENUS_CACHE_MAP`、`TRANS_CACHE_MAP`、`SUBTRANS_CACHE_MAP`、`SUBTRANSBEAN_CACHE_MAP`、`MENUCONDITION_MAP` 等。  
- **操作**：`JedisCacheBase.hgetAll`、`hmset`、`hdel`、`hmget`、`hkeys`、`exists` 等，用于菜单、交易、子交易、权限条件等加载与刷新。  
- **审计/同步**：如 `SysUserService`、`SysUserAudiService`、`RoleAudiService` 中通过 `JedisCacheBase.hgetAll("BizMenusCache.RMap")`、`BizSubTransCacheBean.RMap` 做审计或数据同步。

### 5.4 公共业务缓存（lcpt-pub-cache）

以下缓存均基于 `JedisCacheBase`（部分可能结合本地缓存），用于字典、机构、参数等基础数据：

| 缓存类 | 用途说明 |
|--------|----------|
| DictCache / DictMapCache | 字典及字典映射 |
| BranchCache / BranchProvinceCache / LocalBranchCache | 机构、省机构、本地机构缓存 |
| ParamCache | 公共参数 |
| SysArgCache | 系统参数 |
| TAInfoCache | TA 信息 |
| TransCache / TransStatusCache | 交易配置、交易状态 |
| UserCache | 用户信息 |
| ChannelInfoCache | 渠道信息 |
| FileTemplateCache / FileDataCheckCache | 文件模板、数据校验配置 |
| SmsTemplateCache | 短信模板 |
| ErrorMsgCache | 错误信息 |
| RecommenderCache / PrdManagerInfoCache / PrdTransDayCache | 推荐人、产品经理、产品交易日等 |
| ClientManagerCache / ClientAmlRiskCache / AmlBlackCache | 客户经理、AML 风险、黑名单 |
| OutBankInfoCache / RegionmapCache | 行外信息、地区映射 |
| MonitorFileConCache | 监控文件配置 |
| BusinSysInfoCache | 业务系统信息 |

配置中的 `redis.caches` 可指定启动时需要刷新的缓存 Bean 列表（如 `DxTrustTaInfoCache,SysArgCache`），用于双写或定时刷新场景。

### 5.5 基金（dxfund）相关

- **DxFundProductCache / FullDxFundProductCache 等**：产品信息，Hash 结构（如 `DXFUNDPRODUCT_CACHE_INFO_MAP`、`ENDDXFUNDPRODUCT_CACHE_INFO_MAP`），带时间戳 key 做版本控制。  
- **DxFundIssAmtCache**：发行额度，与 `DxFundRedisIssAmtService` 配合，做额度占用/归还的原子更新（事务 + Redis）。  
- **DxFundTAInfoCache、DxFundPrdParamValueCache、DxFundPrdBranchAllowCache、DxFundPrdCardAllowCache、DxFundPrdBankAccCache、DxFundTransStatusCache 等**：TA、产品参数、分支/卡bin/银行账户、交易状态等。  
- **RedisDxFundPrdIndexMapCacheProxy**：产品索引映射的 Redis 缓存代理（请求/应答/信息 Map 的 JSON 存取）。  

额度服务中直接使用 `JedisFactory.getJedis()` + `Transaction` 做多 key 的原子读写，保证并发下额度一致。

### 5.6 信托（dxtrust）相关

- **RedisDxTrustPrdIndexMapCacheProxy**：与 dxfund 类似，信托产品索引映射的 Redis 代理（Request/Answer/Info Map）。  
- **DxTrustTaInfoCache**：信托 TA 信息缓存，可在 `redis.caches` 中配置刷新。

### 5.7 资产（dxasset）相关

- **DxAssetProductCache**、**RedisDxAssetPrdIndexMapCacheProxy**：产品缓存与产品索引映射，模式与 dxfund/dxtrust 类似。  
- **DxAssetRedisIssAmtService**：资产端额度服务，与基金额度类似，使用 Redis 做额度计数与事务。

### 5.8 发布订阅

- **RedisMsgPubSubListener**（bizframe-core）：继承 `JedisPubSub`，订阅指定 channel；收到以 `msg:` 开头的消息时，用 Redis 分布式锁（setnx + expire 3 秒）保证仅一个实例执行后续逻辑（如用户自动解锁）。  
- **JedisMessageUtils**（lcpt-pub-cache）：  
  - Channel：`lcpt-cache-message-channel`。  
  - 当 `lcpt.redis.broadcast.enable=true` 时，启动时在新线程中调用 `JedisCacheBase.subscribe(JedisSubscribeMessage(), channel)` 订阅；  
  - 提供 `publish(message)` 和 `packMessage(belongType, method, paramMap)`，用于多实例间缓存刷新通知（如本地缓存失效）。  

发布端统一使用 `JedisCacheBase.publishMessage(channel, message)`，支持主备双写时同步发布到备机房。

### 5.9 Redis 键查询与管理

- **RedisSearchService / RedisSearchController**：根据 pattern 查询 Redis 键、过滤 Hash 键等，供管理端查看或排查。  
- **RedisCacheConctoller**：通过接口 `ifmCXtglRedisView/ifmCXtglRedisViewQuery` 按 `cacheCode`（即 Redis Hash key）和 `key`（field）查询缓存内容，返回 JSON。

### 5.10 批量与数据校验

- **RedisDataCheck**（T200120）：从表 `tbredischeckconfig` 读取配置（object_id、redis_key、table_name、query_fields、key_fields），校验指定 Redis 缓存与 DB 表数据是否一致，用于批处理或对账。  
- 部分批处理适配器（如 T210409、T219654、T219409、T210906 等）在流程中会使用产品缓存、额度缓存等，间接依赖 Redis。

---

## 六、涉及的应用模块（按工程）

以下模块的 `application.properties` 或模板中包含 `redis.*` 配置，即可能启用 Redis：

- **lcpt-schedule**：定时任务（含 redis 缓存日志、可选 redis 配置）  
- **lcpt-datax**：数据交换（日志相关）  
- **lcpt-dxtrust**：信托  
  - dxtrust-batch-bootstrap、dxtrust-api-bootstrap、dxtrust-online-query-bootstrap、dxtrust-online-trade-bootstrap  
  - dxtrust-pub-online-bootstrap、dxtrust-pub-batch-bootstrap、dxtrust-pub-api-bootstrap、dxtrust-pub-cache 测试  
- **lcpt-dxfund**：基金（各 trans/pub 的 api、batch、online-query、online-trade、bootstrap）  
- **lcpt-dxasset**：资产（与 dxfund 类似多 bootstrap）  
- **lcpt-web-manager-dxfund**：基金 Web 管理端  

具体是否真正连 Redis 由该模块的 `redis.enabled` 决定。

---

## 七、数据与 Key 约定

- **Key 前缀**：  
  - 分布式锁：`FINA_REDIS_LOCK:`  
  - 业务 key：若配置 `redis.prefix`，则所有通过 `JedisCacheBase` 的 key 会自动加此前缀，便于环境隔离或多租户。  
- **结构**：  
  - 大量使用 **Hash**，一个业务缓存一个 key，field 为业务主键或编码，value 为 JSON 或序列化对象。  
  - 部分为 String（如时间戳、会话、单值配置）。  
  - Set 用于集合关系（如黑白名单、权限集合等）。  
- **过期**：会话、验证码、部分锁使用 `expire` 设置 TTL；很多业务缓存未设过期，依赖主动刷新或 `redis.caches` 刷新机制。

---

## 八、注意事项与建议

1. **连接释放**：使用 `JedisFactory.getJedis()` 时必须配对 `returnResource(jedis)`，避免连接泄漏；`JedisCacheBase` 内部已处理。  
2. **集群限制**：Redis 集群下不支持多 key 事务、部分多 key 操作需注意分片；当前事务与 WATCH 仅在单机/哨兵下使用。  
3. **双写**：备机房不可用时主写仍会成功，需监控 Syn 失败日志，必要时通过 `redis.syn.throwException` 在测试环境打开以提前发现问题。  
4. **密码**：支持密文配置，由 `EncryptFactory.getEncrypt().dec()` 解密；哨兵可单独配置 `redis.sentinelPassword`。  
5. **Spring Session**：依赖中虽有 `spring-session-data-redis`，但当前会话与登录态主要由业务通过 `JedisCacheBase` 自行维护，若未来接入 Spring Session 需统一会话存储与过期策略。  
6. **监控与健康**：`ServiceHealthIndicator`（lcpt-base-core）等会检测 Redis 可用性，可结合现有监控做告警。  
7. **日志**：log4j2 中多处配置了“redis缓存日志”相关 Appender，便于排查缓存与双写问题。

---

## 九、Redis 客户端对比分析：Jedis vs Redisson vs spring-boot-starter-data-redis

### 9.1 整体定位对比

| 对比维度   | Jedis                      | Redisson                | spring-boot-starter-data-redis   |
| --------- | -------------------------- | ----------------------- | --------------------------------- |
| 本质       | Redis Java 原生客户端        | 高级 Redis 分布式框架     | Spring Boot 自动配置集成组件        |
| IO 模型   | BIO（阻塞 IO）              | Netty（NIO，非阻塞）     | 底层依赖 Lettuce 或 Jedis          |
| 线程模型   | 单连接单线程                 | 线程安全                 | 取决于底层客户端                    |
| 功能复杂度  | 基础命令操作                 | 分布式增强功能            | Spring 生态整合                    |
| 适合人群   | 需要直接控制 Redis          | 需要分布式能力            | Spring Boot 项目                   |

### 9.2 Jedis

**优点**

1. API 简单直观，与 Redis 命令高度一致  
2. 性能开销小（低并发场景表现好）  
3. 成熟稳定，使用历史悠久  
4. 易于理解底层通信机制  

**缺点**

1. **线程不安全**（必须使用连接池）  
2. 基于 BIO，高并发下线程成本高  
3. 不支持响应式编程  
4. 分布式功能需要自行实现  

**适用场景**

- 中小型系统  
- 并发量不高  
- 需要精细控制 Redis 命令  
- 非响应式项目  

### 9.3 Redisson

Redisson 不只是 Redis 客户端，而是一个基于 Redis 的“分布式工具框架”。

**优点**

1. 线程安全  
2. 基于 Netty，支持高并发  
3. 提供大量分布式组件：  
   - 分布式锁  
   - 分布式队列  
   - 分布式信号量  
   - 延迟队列  
   - 分布式 Map、List  
4. 提供 Java 对象映射  
5. 支持异步、响应式 API  

**缺点**

1. 体积较大，引入成本高  
2. 学习曲线较陡  
3. 对简单场景来说过于复杂  
4. 内部封装较多，调优难度相对大  

**适用场景**

- 分布式系统  
- 微服务架构  
- 需要分布式锁、延迟队列  
- 高并发系统  

### 9.4 spring-boot-starter-data-redis

本质是 Spring Boot 对 Redis 的自动配置整合组件，默认使用 Lettuce（可切换为 Jedis）。

**优点**

1. 自动配置，开箱即用  
2. 与 Spring Cache 无缝整合  
3. 支持事务、序列化策略配置  
4. 支持响应式 Redis  
5. 易于与 Spring 生态整合  

**缺点**

1. 本身不是客户端，而是封装层  
2. 功能能力取决于底层客户端  
3. 高级分布式能力仍需 Redisson 等补充  

**适用场景**

- 所有 Spring Boot 项目  
- 需要缓存抽象  
- 统一配置管理  

### 9.5 线程模型对比

| 组件                   | 是否线程安全 | 并发模型         |
| ---------------------- | ------------ | ---------------- |
| Jedis                  | ❌ 否        | 一连接一线程       |
| Redisson               | ✅ 是        | Netty 事件驱动   |
| Spring Boot Starter    | 取决于底层   | 通常为 Netty     |

### 9.6 架构层级理解

可以从“层级”理解三者关系：

```
Spring Boot
   ↓
spring-boot-starter-data-redis
   ↓
Lettuce 或 Jedis
   ↓
Redis Server
```

Redisson 则是：

```
业务代码
   ↓
Redisson 分布式组件
   ↓
Redis Server
```

### 9.7 选型建议

| 场景                         | 推荐 |
| ---------------------------- | ---- |
| 传统项目 + 并发不高           | Jedis |
| Spring Boot 项目             | spring-boot-starter-data-redis（默认 Lettuce） |
| 分布式系统 / 微服务           | Redisson |
| 高并发系统                   | Lettuce 或 Redisson |

### 9.8 小结

- **Jedis** = 轻量级基础客户端  
- **Redisson** = 分布式增强框架  
- **spring-boot-starter-data-redis** = Spring 生态整合层  

如果只是“操作 Redis”，Jedis 足够；如果是“构建分布式系统”，Redisson 更合适；如果在 Spring Boot 项目中开发，优先使用 spring-boot-starter-data-redis 进行统一管理。  

**与本项目的关系**：当前工程采用 Jedis + 自研 JedisCacheBase 封装，适合现有并发与业务控制需求；若后续需要更强分布式能力（如复杂锁、延迟队列），可评估引入 Redisson 或保留 Jedis 并继续在应用层扩展。

---

## 十、文档与版本说明

- 本文基于对 `app` 目录下 Redis 相关代码与配置的静态分析整理。  
- 涉及版本：综合理财管理平台 V6.0，Redis 客户端 Jedis，Spring Boot/Spring Cloud 由 lcpt-boot-dependencies 等统一管理。  
- 若新增 Redis 使用场景，建议继续通过 `JedisCacheBase` 和现有 Cache 封装，以保持配置、双写、前缀和监控行为一致。

---

**文档生成日期**：2025-02-13  
**分析范围**：/Users/zhoufz/hundsun/lcpt60/git/Sources/app
