# 总体设计 v1（02-design）

> 状态：v1.0 待评审。本文件回答「为什么这样切、模块怎么连、不变量是什么」。
> **不在这里定义字段、枚举、错误码**——一律看 `02b-contract.md`。本文件出现的 `§x.y` 引用均指向契约小节。

## 1. 系统全景

### 1.1 容器与端口

| 容器 | 镜像/来源 | 端口 | 作用 |
|---|---|---|---|
| `app` | 本仓库构建（`:app` jar，含前端静态资源） | 8080 | 唯一业务进程（模块化单体） |
| `keycloak` | `quay.io/keycloak/keycloak:26.x` | 8081 | OIDC 提供方（PKCE + client_credentials） |
| `postgres` | `postgres:16` | 5432 | 业务库 + Flyway 目标 |
| `kafka` | `apache/kafka:3.8.x`（KRaft 单节点） | 9092 | 告警入口 + 清除事件 |

依赖顺序：`postgres`、`kafka`、`keycloak` 各有 healthcheck，`app` 用 `depends_on: service_healthy`。
本机验收命令与 Docker/psql 替代写法见 §9.3。

### 1.2 进程内模块

```
app ──> security, alert, audit, ai, platform, notify
alert ──> platform, security
audit ──> platform, security
ai ──> alert(只读端口 AlertReadPort), platform, security
notify ──> platform
platform ──> （无业务依赖）
```

- 跨模块只允许三种通道：**领域事件（platform `DomainEventBus`）**、**注解（platform `@Auditable` / `@RequirePermission`）**、**显式端口接口**（`AlertReadPort` 在 alert；`AccessDeniedAuditPort` / `AuditTargetResolver` 在 platform，由 audit/alert 实现）。
- ArchUnit 每条规则见 §10；违规即构建失败。

### 1.3 前端

React 19 + TS + Vite + TanStack Query + Tailwind；`oidc-client-ts` 管 PKCE 与内存 token；类型由 `docs/api/openapi.yaml` 生成（命令见 `02b-contract.md §7`）。构建产物由 `:app` 的 Gradle 任务拷进 `static/`，单 jar 交付。

### 1.4 关键取舍（详见 ADR）

| 取舍 | 结论 | ADR |
|---|---|---|
| 是否引入网关做鉴权 | 否，Casbin 在应用内 | 0001 |
| 活动/历史是否单表加状态列 | 否，双表 + 单事务原子移动 | 0002 |
| 去重是否有时间窗 | 无时间窗 | 0003 |
| 403 审计怎么跨模块写 | platform 端口，由 audit 实现 | 0004 |
| 审计里的告警快照从哪来 | platform SPI，由 alert 提供 | 0005 |
| 是否保存每条上报原文 | 是，`alert_event` 保留最近 50 条/告警 | 0006 |
| Kafka 事件 DTO 放哪 | platform（notify 不能依赖 alert） | 0007 |
| AI 如何保证不动手 | 只依赖只读端口 + 输出不落库 | 0008 |
| 三条可选需求 | 二期 | 0009 |

## 2. 数据模型与不变量

> 逐列定义见 `02b-contract.md §2`。本节只说**表为什么这么分、必须永远成立什么**。

### 2.1 表清单与 `alerts_active`

7 张表：`alerts_active`、`alerts_history`、`alert_event`、`audit_event`、`processed_event`、`keycloak_cursor`、`notification`。

`alerts_active` 装载**未清除**的告警，**一个 fingerprint 只能有一行**（唯一索引）。它同时承担「聚合体」：`occurrence_count`、`first_seen_at`、`last_seen_at`、当前最高 `severity`、最新 `message`/`latest_payload`。

不变量：

- **I-A1** `fingerprint` 全表唯一（唯一索引兜底，不靠应用判断）。
- **I-A2** `occurrence_count ≥ 1`，每次成功摄入 +1，绝不重置。
- **I-A3** `first_seen_at ≤ last_seen_at`；`last_seen_at` 只前进不后退（`greatest` 语义）。
- **I-A4** `severity` 只升不降（按 rank 比较），见 ADR-0003。
- **I-A5** 每行必有 `latest_event_id` 指向一条 `alert_event`；两者同事务写入。

### 2.2 `alerts_history`

历史告警**只增不改**。与 `alerts_active` 列几乎相同，另加 `cleared_at / cleared_by / cleared_reason / clear_mode`。

不变量：

- **I-H1** 同一 `id` 在 `alerts_active` 与 `alerts_history` 中**不允许同时存在**（移动是原子的）。
- **I-H2** `alerts_history.id` 为主键：同一告警第二次清除必然冲突，因此不可能出现两条历史。
- **I-H3** 历史行的 `occurrence_count` 等于移动那一刻活动行的值，之后不再变化（清除后再报是新行/新 id）。
- **I-H4** `cleared_by` 非空；系统清除时写 `system`（本期不产生，列先留）。

### 2.3 审计相关表与 `audit_event`

`audit_event` 是**唯一**的「谁、何时、对什么、做了什么、结果如何」事实表：登录 / 登出 / 登录失败 / 清除 / 越权全部落这里，US-C4 的「混排」靠 `occurred_at` 统一排序实现。

不变量：

- **I-C1** 审计写入用 `REQUIRES_NEW`，与主业务事务**无耦合**：主业务回滚不影响已写审计；审计写失败不影响主业务（catch + 计数 + error 日志）。
- **I-C2** `detail` 只允许放白名单字段，**永不出现** token / 密码 / 请求体全文 / 完整 IP。
- **I-C3** `source='KEYCLOAK'` 的行必须带 `source_event_id`，且该列唯一（重复拉取幂等）。
- **I-C4** `401` 不产生审计（防扫描刷表）；`403` 必须产生一条 `outcome='DENIED'`。
- **I-C5** 审计行没有任何更新/删除接口（应用层无写路径）。

### 2.4 支撑表

| 表 | 作用 | 不变量 |
|---|---|---|
| `alert_event` | 每次上报的原文（证据链，供 US-B4 / US-D1 引用） | 只增；每 alert 只保留最近 50 条（同事务裁剪）；不设 FK（告警会跨表移动） |
| `processed_event` | Kafka 消费幂等台账 | 主键 `(consumer_group, topic, partition, record_offset)`；插入冲突即视为重复，直接跳过 |
| `keycloak_cursor` | 事件采集游标 | 单行 `id='realm-events'`；`last_event_time` 只前进 |
| `notification` | 清除事件的下游落点（证明链路通） | 主键 `id`；`alert_id` 非空；只增 |

### 2.5 去重不变量与 fingerprint 规范

**四段**：`source`、`type`、`resourceId`、`eventCode`（与 US-B2 一致，不含 message / occurredAt）。

规范化顺序：`null → ""`；`trim`；`toLowerCase(Locale.ROOT)`；四段按固定顺序用 `\u001F` 拼接；对结果做 `SHA-256`，取 64 位小写 hex。完整定义见 `02b-contract.md §1.5`。

必须永远成立：

- **I-D1** 客户端传来的 `fingerprint` **一律忽略**（服务端重算）。
- **I-D2** 去重只靠单条 `insert ... on conflict (fingerprint) do update`；**禁止先 select 再 insert**，禁止 Java 端加锁 / `synchronized`。
- **I-D3** 描述里的数值、时间、大小写差异**不影响**合并（不参与 fingerprint）。
- **I-D4** 并发 N 个同指纹请求，最终只有 1 行且 `occurrence_count = N`（唯一索引 + upsert 保证）。

> 时间窗：US-B2 提到「10 分钟」，本期按 ADR-0003 **不实现窗口**——活动期内一直合并，清除后再报为新行。原文意图（不刷屏）已满足，且避免「窗口边界产生第二条活动行」导致列表出现两条同故障。

### 2.6 清除的原子移动不变量

**I-M1** 一次清除 = 一个 `@Transactional` service 方法，内含两条语句、顺序固定：先 `insert into alerts_history ... select from alerts_active where id=? on conflict (id) do nothing`，再 `delete from alerts_active where id=?`。**禁止拆成两个事务**，禁止先 delete 后 insert。

**I-M2** 两语句在同一事务内，任一步失败整体回滚（活动行仍在）。

**I-M3** 错误分类只看写入结果：insert 0 行 → `ALREADY_CLEARED`(409)；insert 0 行且 history 无该 id → `ALERT_NOT_FOUND`(404)。

**I-M4** 幂等靠主键，不靠应用层锁：并发两次清除，一成一败（409），历史仍 1 行。

**I-M5** 事务提交后才发布 `alert.cleared`（`AFTER_COMMIT`），回滚不得发出任何消息（见 §5.3）。

## 3. 接口契约总表

> 字段级契约在 `02b-contract.md §3`（编号一致）。这里只做「端点 → 模块 → 权限 → US → T」的地图。

| # | 方法与路径 | 模块 | 权限 `obj:act` | 覆盖 US | 承接 T |
|---|---|---|---|---|---|
| 1 | `GET /api/alerts/active` | alert | `alert:read` | B3 | T05 |
| 2 | `GET /api/alerts/history` | alert | `alert:read` | B6 | T07 |
| 3 | `GET /api/alerts/{id}` | alert | `alert:read` | B4 | T05 |
| 4 | `POST /api/alerts/{id}/clear` | alert | `alert:clear` | B5 A4 C2 C3 | T06 |
| 5 | `POST /api/alerts/ingest` | alert | `alert:ingest` | B1 B2 | T04 |
| 6 | `GET /api/audit/events` | audit | `audit:read` | C1 C2 C3 C4 | T08 T09 |
| 7 | `GET /api/audit/events/export` | audit | `audit:export` | C4 | T09 |
| 8 | `POST /api/ai/briefing/{id}` | ai | `ai:briefing` | D1 D4 | T11 |
| 9 | `POST /api/ai/query` | ai | `ai:query` | D2 D4 | T12 |
| 10 | `GET /api/ai/digest`（二期） | ai | `ai:digest` | D3 | — |
| 11 | `GET /api/whoami` | app（聚合 security） | `whoami:read` | A1 A3 | T02 |
| 12 | `GET /api/_perm/check`（仅 `!prod`） | security | `_perm:check` | A3 A5 | T03 |

> `#10` 属二期：契约位保留、本期不实现（请求 404）。`#12` 仅 `@Profile("!prod")`。

约定：

- 除 `/actuator/health` 与前端静态资源外，**所有路径都要求认证**（NFR-05）。
- 所有 handler 必须有 `@RequirePermission`；**没有注解 = 默认拒绝（403）**，不允许「忘记加注解就放行」。
- 错误响应统一 envelope（契约 §0.3）；集合一律返回空数组，不返回 null。
- 时间一律 ISO-8601 UTC 字符串（`2026-11-23T02:00:00Z`），Java `Instant`，DB `timestamptz`。
- 分页统一 `page`（0 基）+ `size`（默认 20，上限 100，超限按 100 返回而不报错）。

## 4. 授权模型

### 4.1 模型（`security/src/main/resources/casbin/model.conf`）

请求三元组 `r = sub, obj, act`；策略 `p = sub, obj, act`；匹配器为**精确相等**：

```
m = r.sub == p.sub && r.obj == p.obj && r.act == p.act
```

明确禁止 `keyMatch` / `regexMatch` / URL 通配：本项目没有「按 URL 授权」的需求，URL 通配会引入绕过（ADR-0001）。

### 4.2 obj/act 字典

见 `02b-contract.md §5.1`：五个 obj（`alert/audit/ai/whoami/_perm`）与八个 act（`read/ingest/clear/export/briefing/query/digest/check`）。新增 obj/act 必须先改契约。

### 4.3 JWT → sub 映射与多角色求值

- 角色来源：Keycloak realm roles，取 JWT 的 `realm_access.roles`；**不查库**、不同步用户。
- 过滤：只保留 `admin | operator | viewer | service` 四个受管角色，其余（`offline_access`、`default-roles-*`、`uma_authorization` 等）忽略。
- **多角色求值**：对每个受管角色分别 `enforce(role, obj, act)`，**任一 allow 即放行**。`admin` 的策略行已显式包含 operator 的全部能力，不依赖 OR 继承。
- 没有任何受管角色 → 403。
- 不做结果缓存（策略文件小、`enforce` 微秒级）；策略变更需重启生效（US-A5 验收口径）。
- 前端不自行判断权限：按钮可用性由 `#11 /api/whoami` 返回的 `permissions` 决定，避免前端硬编码角色。

### 4.4 策略矩阵（`policy.csv` 摘要，逐行见契约 §5.2）

| 角色 | alert | audit | ai | whoami | _perm |
|---|---|---|---|---|---|
| `admin` | read, clear, ingest | read, export | briefing, query | read | check |
| `operator` | read, clear | read | briefing, query | read | — |
| `viewer` | read | read | briefing, query | read | — |
| `service` | ingest | — | — | — | — |

## 5. 消息链路

### 5.1 拓扑

| Topic | 生产者 | 消费者（group） | Key | 用途 |
|---|---|---|---|---|
| `alert.ingested` | 外部设备 / simulator | `triagedeck-alert-ingest`（alert 模块） | `fingerprint` | 第二入口上报，与 HTTP 走**同一个**摄入 service |
| `alert.cleared` | alert（事务提交后） | `triagedeck-notify`（notify 模块） | `alertId` | 下游通知落库 |
| `alert.ingested.dlt` | 消费失败处理 | 人工排查 | 原 key | 非法 JSON / 业务校验失败超过重试次数 |
| `alert.cleared.dlt` | 消费失败处理 | 人工排查 | 原 key | 同上 |

分区数 1（单节点）；`auto.create.topics.enable=false`，topic 由 compose 初始化脚本显式创建。
compose 的 kafka 有两个监听器：容器内 `PLAINTEXT://kafka:9092`、宿主 `EXTERNAL://localhost:19092`（映射 19092）。
本地开发（app/模拟器跑在宿主机）用 Homebrew Kafka 的 9092，topic 用 `scripts/create-kafka-topics.sh` 显式创建；
全栈容器时宿主工具投消息用 `--bootstrap=localhost:19092`——两者是同一集群的两个接入点。

### 5.2 事件契约归属

DTO 放 `platform`：`AlertIngestedMessage`、`AlertClearedMessage`（字段见 `02b-contract.md §6.3`）。
原因：`notify → platform` 且**不能**依赖 `alert`，若 DTO 放 alert，notify 必然违规 import（ADR-0007）。

### 5.3 发布时机

- `alert.cleared` 只能在**主事务提交后**发出：`DomainEventBus.publishAfterCommit(...)`（内部用 `TransactionSynchronization.afterCommit`，无事务时立即发）。
- 回滚 → 一条都不发（T10 验收项）。
- 禁止在 `@Transactional` 方法体内直接 `kafkaTemplate.send(...)`。

### 5.4 消费幂等

`processed_event` 主键 `(consumer_group, topic, partition, record_offset)`；消费入口先 `insert ... on conflict do nothing`，影响 0 行 → 直接 ack 跳过。
**不能用消息体 hash 做幂等键**：同一条告警的 10 次真实上报内容可能完全相同，但必须计 10 次。

### 5.5 失败与 DLT

`DefaultErrorHandler` + `FixedBackOff(1000ms, 2 次)` + `DeadLetterPublishingRecoverer`；DLT 后缀由 `app.kafka.dlt-suffix=.dlt` 拼接，**不硬编码 topic 字符串**。
消费者不允许吞异常后返回正常（否则消息被 ack、永久丢失）。

## 6. 审计模型

### 6.1 三条写入路径

| 路径 | 触发 | 位置 | 事务 |
|---|---|---|---|
| 业务动作成功/失败 | `@Auditable` 切面 | `audit` 模块 `AuditableAspect` | 业务事务结束之后；审计本身 `REQUIRES_NEW` |
| 越权被拒 | `JsonAccessDeniedHandler` | `security`，调用 platform 端口 `AccessDeniedAuditPort` | `REQUIRES_NEW` |
| 登录/登出/登录失败 | 定时采集 Keycloak 事件 | `audit` 模块 `KeycloakEventCollector` | 单事务批量写入 |

**为什么 403 不走 `@RestControllerAdvice`**：Spring Security 的 `ExceptionTranslationFilter` 在过滤器链里就处理掉了 `AccessDeniedException`，`@ControllerAdvice` 根本不触发。写在 handler 里是唯一可靠位置。401 不记审计（防扫描刷表）。

### 6.2 切面与事务的顺序

`@Auditable` 切面 `@Order(Ordered.LOWEST_PRECEDENCE - 10)`（在事务拦截器**外层**）：切面进入 → 事务开启 → 业务 → 提交/回滚 → 切面写审计。
这样才满足「主业务回滚，审计仍在」。

### 6.3 审计详情来源（跨模块）

切面在业务结束（提交或回滚）后，按 `targetType` 找 platform SPI `AuditTargetResolver` 的实现（`alert` 提供 `ALERT` 的实现）取**只读快照**（severity/source/type/resourceId/occurrenceCount/message 摘要）写进 `detail`。
失败场景拿不到快照时退化为 `{path, method, errorClass}`——**不允许把异常 message 原文落库**。

### 6.4 采集与游标

- 每 15 秒一轮（满足 US-C1「最长 30 秒」），Admin API 用 client_credentials（**绝不用用户 token**）。
- 游标 `(last_event_time, last_event_id)`；每轮 `dateFrom=last_event_time` 拉取 → 靠 `audit_event.source_event_id` 唯一约束去重。
- 连续两轮 0 条新事件 → `eventsCollected=false`；realm `eventsEnabled=false` → `eventsCollected=false, eventsDisabled=true`（前端提示「未采集到数据」而非空白）。

## 7. 认证与会话

### 7.1 SPA PKCE 时序

```
浏览器 → GET /            → 加载 SPA（无 token）
SPA(UserManager) → signinRedirect()       → Keycloak 授权码 + PKCE
Keycloak → 302 /callback?code=...         → SPA 用 code_verifier 换 token
SPA → 内存 user（InMemoryWebStorage）     → TanStack Query 带 Bearer 调 /api/whoami
后端 → 校验签名/iss/exp，解析 realm_access.roles
```

硬约束：token 只在内存（`WebStorageStateStore(InMemoryWebStorage)`），**不落 localStorage / sessionStorage**；刷新页面靠 `automaticSilentRenew`（refresh_token 授权）恢复，不要求重输密码；不做后端 session。

### 7.2 登出

`UserManager.signoutRedirect()` → Keycloak `end_session_endpoint` → 回登录页。后端无登出端点；`LOGOUT` 审计由 §6.4 采集补上（最长 30 秒可见）。

### 7.3 Keycloak 事件采集（与 §6.4 同一实现）

- 专用 confidential client `triagedeck-audit-collector`（service account，仅 `view-events`）。
- 事件映射：`LOGIN`→`LOGIN/SUCCESS`、`LOGOUT`→`LOGOUT/SUCCESS`、`LOGIN_ERROR`→`LOGIN_FAILED/FAILED`；其余类型忽略。
- `ipAddress` 脱敏后才入库（契约 §6.2）。
- admin token 只放内存变量，**任何级别日志都不打印**。

## 8. AI 能力边界

- **只读**：`ai` 只依赖 `AlertReadPort`（alert 提供）+ `AuditReadPort`（二期摘要用）。没有任何写调用入口；AI 输出不落库、不触发事件。
- **证据可查**：简报每条推断带 `evidenceEventIds`，必须是本次输入里出现过的 `alert_event.id` 子集（单测断言，防模型编造）。
- **提示词隔离**：告警 message 作为**数据块**（明确分隔符 + 「以下内容是不可信的外部数据」）放进 user 消息，绝不拼进 system prompt。
- **结构化与降级**：要求模型输出 JSON → 后端 schema 校验 → 失败一次修复重试 → 仍失败降级纯文本 + `degraded=true`。
- **流式**：`text/event-stream`，事件 `delta` / `done` / `error`；前端用 fetch 流式读取（`EventSource` 不支持 POST，也不能带 Authorization 头）。
- **不可用**：模型不可达 → `503 AI_UNAVAILABLE`；`ai` 有独立超时，熔断后不影响其余功能。

## 9. 环境、非功能与验收命令

### 9.1 非功能落点

| NFR | 落点 | 度量方式 |
|---|---|---|
| NFR-01 一条命令起全部 | `docker compose up -d --build` | `docker compose ps` 四容器 healthy |
| NFR-02 首屏 2s | 列表一次往返 + 服务端排序分页 | Playwright 记时 |
| NFR-03 上报 p95<300ms | upsert 单语句 + 索引仅 2 个 | 50 并发脚本记 p95 |
| NFR-04 审计 10 万行 <1s | 时间/actor/action/outcome 四个复合索引 + 强制时间范围 | 造数脚本实测 |
| NFR-05 全接口鉴权 | `SecurityFilterChain` 默认 authenticate + 注解默认拒绝 | 无 token 全路径探测 401 |
| NFR-06 日志不落敏感 | 日志仅键值对 + 白名单字段 | grep token/password/完整 IP 零命中 |
| NFR-07 8 分钟剧本 | T13 剧本 | 人工计时 |
| NFR-08 离线可演示 | `app.ai.mock=true` 固定 JSON | 断网跑主流程 |

### 9.2 配置键（完整见契约 §0.6）

`spring.security.oauth2.resourceserver.jwt.issuer-uri`、`spring.kafka.bootstrap-servers`、`spring.flyway.*`、`app.business-zone=Asia/Shanghai`、`app.kafka.dlt-suffix`、`app.ai.*`、`app.audit.collect-interval=15s`、`app.keycloak.admin.*`。**不手写** `ProducerFactory` / `KafkaTemplate` bean。

### 9.3 本机环境前提与命令改写

实测本机：Java 21 ✅、Node 22 ✅、Gradle ❌（未安装）、Docker CLI ✅ 但 daemon 未运行、**无 compose 插件**、psql ❌。

| 原验收写法 | 本机可执行写法 |
|---|---|
| `./gradlew build` | 先引导 wrapper（`gradle wrapper` 或从 start.spring.io 生成工程），之后可用 |
| `docker compose up -d` | 启动 Docker Desktop 并安装 compose v2 插件（`docker compose version` 必须可用） |
| `psql -c "\dt"` | `docker compose exec postgres psql -U triagedeck -c "\dt"` |
| `curl localhost:8080/actuator/health` | 不变 |

**这些是环境前提，不是文档缺陷**；T01 开工前必须满足，否则验收命令不可复现。临时排查可用 `docker run` 单容器起 postgres，但不满足 NFR-01 的 compose 验收。

## 10. 模块依赖与 ArchUnit 规则

每模块一条 ArchUnit 测试，至少包含：

1. `alert` / `audit` 互不依赖（双向断言）。
2. 任何模块不得依赖他人 `..repo..`；只允许经端口接口。
3. `platform` 不得依赖任何业务模块。
4. `ai` 只允许依赖 `..alert.api.port..`（`AlertReadPort` 所在包），不得依赖 alert 的 `service/domain/repo`。
5. `notify` 不得依赖 `alert` / `audit` / `security`。
6. `ai` 包内不得出现 repo 类与写方法（AI 不动手，US-D4）。
7. Controller（`..api..`）不得直接依赖 Repository（AGENTS §2.1.4）。

## 11. ADR 索引

| ADR | 标题 | 影响面 |
|---|---|---|
| 0001 | 授权在应用内（Casbin），不设网关 | `security`、契约 §5 |
| 0002 | 活动/历史双表 + 原子移动 | DDL、`lld/02` |
| 0003 | 去重不设时间窗、severity 只升不降 | `lld/02`、US-B2 解释 |
| 0004 | 403 审计经 platform 端口写入 | `security`、`audit`、`platform` |
| 0005 | 审计详情由目标模块 SPI 提供 | `audit`、`alert`、`platform` |
| 0006 | 新增 `alert_event` 保存每次上报原文 | DDL、`lld/02`、`lld/04` |
| 0007 | Kafka 事件 DTO 归 platform | `platform`、`notify`、`alert` |
| 0008 | AI 只读端口、SSE、mock 降级 | `ai`、`lld/04` |
| 0009 | 三条可选需求移二期 | 范围、追溯矩阵 |
| 0010 | 统一时间与时区（存储 UTC，业务区 Asia/Shanghai） | 全局、NL 查询 |

## 12. 文档地图与冻结状态

见 `docs/00-index.md`。本文件冻结后，任何改动必须先有 ADR 并同步改契约，否则视为不合格产出。

