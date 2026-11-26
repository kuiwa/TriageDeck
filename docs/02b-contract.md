# 字段级契约 v1（02b-contract）

> 状态：v1.0 待评审。**凡涉及字段名、类型、可空、默认值、枚举值、错误码、状态码，一律以本文件为准。**
> 其他文档只引用小节号，不复制字段。改本文件 = 改契约：必须先有 ADR，再同步改 LLD，最后改代码。

## 0. 通用约定

### 0.1 ID、时间、时区

| 项 | 约定 |
|---|---|
| 主键 | `UUID`，列名 `id`，由应用生成（`UUID.randomUUID()`），DB 不装 `pgcrypto` |
| 时间列 | DB `timestamptz`；Java `Instant`；JSON 一律 ISO-8601 UTC，带 `Z`（`2026-11-23T02:00:00Z`） |
| 解析容忍 | 入参接受带偏移量的 ISO-8601，服务端统一转 UTC |
| 业务时区 | `app.business-zone=Asia/Shanghai`；只用于「昨天下午」这类自然语言解析与前端展示，**存储永远是 UTC**（ADR-0010） |
| 时钟偏移容忍 | `occurredAt` 允许比服务器时间晚 ≤ 5 分钟，无过去时间下限 |

### 0.2 分页与排序

| 项 | 约定 |
|---|---|
| 参数 | `page`（0 基，默认 0，负数 400）、`size`（默认 20，`>100` 按 100 返回**不报错**，`<1` 400） |
| 返回 | `{ "items": [], "total": 0, "page": 0, "size": 20 }`；`items` 永不为 null |
| 排序 | 全部服务端固定，不接受客户端 `sort`/`orderBy` 参数（出现即 400 `VALIDATION_FAILED`） |
| 列表排序 | 活动：`severity_rank DESC, last_seen_at DESC, id`；历史：`cleared_at DESC, id`；审计：`occurred_at DESC, id` |

### 0.3 错误响应 envelope

```json
{ "code": "ALREADY_CLEARED", "message": "告警已被清除", "traceId": "3f2a1c9e8b7d4a10" }
```

- 三条字段**永远存在**；`message` 为可读中文，不含堆栈/不变量/表名。
- `traceId` 来自请求头 `X-Trace-Id`（无则服务端生成），同时写入审计 `trace_id`。
- `Content-Type: application/json`；CSV 端点的错误也返回 JSON（`Accept` 冲突时仅此例外）。
- 401 由 `JsonAuthenticationEntryPoint` 输出；403 由 `JsonAccessDeniedHandler` 输出（并写审计）。

### 0.4 空值与集合

- 集合字段（`items` / `roles` / `permissions` / `evidenceEventIds`）一律空数组，不返回 `null`。
- 可空标量（`clearedReason` / `targetId` / `clientNet` / `sourceEventId`）返回 `null`，不返回 `""`。
- 请求体中的未知字段：**忽略**（不 400），但分页/排序类未知参数必须 400，防止「客户端以为生效了」。

### 0.5 命名与 DTO 约定

| 项 | 约定 |
|---|---|
| Java 包根 | `io.github.wangkui.triagedeck.<module>.<layer>`，layer ∈ `api/service/domain/repo/config` |
| DTO | `record`，后缀 `Request` / `View` / `Detail`，放 `api` 包 |
| 实体 | JPA `@Entity`，放 `domain`，不直接序列化给前端 |
| JSON 命名 | camelCase；DB 命名 snake_case；两者显式映射（不用隐式策略猜测） |
| 枚举 JSON | 输出枚举名（`CRITICAL`），不做小写转换 |

### 0.6 配置键（唯一清单）

| 键 | 默认 | 说明 |
|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | env `KEYCLOAK_ISSUER_URI` | 资源服务器校验 |
| `spring.datasource.url/username/password` | env | Postgres |
| `spring.flyway.enabled` | `true` | 迁移 |
| `spring.kafka.bootstrap-servers` | env | Kafka（自动配置，不手写 bean） |
| `app.kafka.dlt-suffix` | `.dlt` | DLT topic 后缀 |
| `app.kafka.topics.ingested` | `alert.ingested` | 入口 topic |
| `app.kafka.topics.cleared` | `alert.cleared` | 清除 topic |
| `app.business-zone` | `Asia/Shanghai` | NL 解析与展示 |
| `app.audit.collect-interval` | `15s` | Keycloak 采集间隔 |
| `app.audit.collect-page-size` | `100` | 每页事件数 |
| `app.audit.collect-max-pages` | `5` | 单轮最多拉取页数（防积压） |
| `app.keycloak.admin.base-url` | `http://keycloak:8081` | Admin API |
| `app.keycloak.admin.realm` | `triagedeck` | realm |
| `app.keycloak.admin.client-id` | `triagedeck-audit-collector` | client_credentials |
| `app.keycloak.admin.client-secret` | env `KC_COLLECTOR_SECRET` | 只从环境读，不写默认值 |
| `app.ai.enabled` | `true` | 关闭后 `#8/#9` 返回 503 |
| `app.ai.mock` | `false` | 离线演示/单测用固定 JSON |
| `app.ai.base-url` / `app.ai.api-key` / `app.ai.model` | env | OpenAI 兼容端点 |
| `app.ai.timeout` | `60s` | 单次调用超时 |
| `app.ai.sse-timeout` | `120s` | SSE 总时长 |
| `app.ai.max-concurrent-briefings` | `8` | 并发简报上限（信号量） |
| `app.kafka.consumer.group.ingest` | `triagedeck-alert-ingest` | `alert.ingested` 消费组 |
| `app.kafka.consumer.group.notify` | `triagedeck-notify` | `alert.cleared` 消费组 |
| `app.security.test-endpoints` | `false` | `#12` 开关，prod 必须 false |

## 1. 枚举与字典

### 1.1 Severity

| 枚举 | rank | 中文 | 备注 |
|---|---|---|---|
| `CRITICAL` | 4 | 严重 | |
| `HIGH` | 3 | 高 | |
| `MEDIUM` | 2 | 中 | 默认值（缺省时使用） |
| `LOW` | 1 | 低 | |
| `INFO` | 0 | 提示 | |

- **不接受** `WARN` / `CRIT` / `ERROR` / `warning` 等别名（400 `VALIDATION_FAILED`）。
- 合并规则：`max(rank(旧), rank(新))`，**只升不降**（ADR-0003）。

### 1.2 Outcome / ActorType

| 枚举 | 取值 |
|---|---|
| `Outcome` | `SUCCESS` / `DENIED` / `FAILED` |
| `ActorType` | `USER` / `SYSTEM` / `SERVICE` |

### 1.3 AuditAction / TargetType

| `AuditAction` | 含义 | `targetType` | `targetId` | 来源 |
|---|---|---|---|---|
| `LOGIN` | 登录成功 | `AUTH` | `null` | KEYCLOAK |
| `LOGOUT` | 登出 | `AUTH` | `null` | KEYCLOAK |
| `LOGIN_FAILED` | 登录失败 | `AUTH` | `null` | KEYCLOAK |
| `ALERT_CLEARED` | 清除告警 | `ALERT` | 告警 UUID | 切面 |
| `ACCESS_DENIED` | 越权被拒 | `ENDPOINT` | `obj:act`（如 `alert:clear`） | 403 handler |

`TargetType` 取值：`ALERT` / `AUTH` / `ENDPOINT`。新增动作必须先改本表。

### 1.4 审计来源 / Ingress / ClearMode / Channel

| 枚举 | 取值 | 用途 |
|---|---|---|
| `AuditSource` | `APP` / `KEYCLOAK` | `audit_event.source` |
| `Ingress` | `HTTP` / `KAFKA` | `alert_event.ingress` |
| `ClearMode` | `MANUAL` / `SYSTEM` | `alerts_history.clear_mode`（本期只产生 MANUAL） |
| `NotificationChannel` | `LOG` | `notification.channel`（二期扩展） |

### 1.5 fingerprint 规范

参与四段（顺序固定）：`source` → `type` → `resourceId` → `eventCode`。

规范化：

1. `null` → `""`；对每段 `strip()`（去首尾空白）。
2. `toLowerCase(Locale.ROOT)`。
3. 四段用 `\u001F`（Unit Separator）连接，得到 `material`。
4. `sha256(material)` → 64 位小写 hex，写入 `fingerprint char(64)`。

示例：`source=" Payment-API "`, `type="5xx"`, `resourceId="pay-01"`, `eventCode=null`
→ material = `payment-api\u001F5xx\u001fpay-01\u001F`
→ fingerprint = 该串的 SHA-256。

约束：

- 客户端传来的 `fingerprint` **一律忽略**（请求体不接受该字段，传了也丢弃）。
- `message`、`occurredAt`、`severity`、`payload` **不参与**。
- 规范化必须纯函数、无 IO，单测覆盖：大小写、首尾空格、`null` 与 `""` 等价、四段顺序不可交换。

### 1.6 字段限制表

| 字段 | 限制 | 越界行为 |
|---|---|---|
| `source` / `type` / `resourceId` | 必填；strip 后 1..128 | 400 `VALIDATION_FAILED` |
| `eventCode` | 可选；strip 后 0..128 | 400（若 >128） |
| `severity` | 必填；`1.1` 五值之一 | 400 |
| `message` | 必填；strip 后 1..4000 | 400 |
| `occurredAt` | 必填；允许比服务器时间晚 ≤ 5 分钟；无过去时间下限 | 400 |
| `payload` | 可选；JSON **对象**；序列化后 ≤ 64KB；不存非对象类型 | 400 |
| `clearedReason` | 可选；0..500 | 400 |
| `keyword`（查询） | 可选；0..100 | 400 |
| `actor` / `clearedBy`（查询入参） | 可选；1..128 | 400 |
| AI `question` | 必填；1..500 | 400 |
| 查询时间范围 | `from ≤ to`；`to - from ≤ 90 天`；审计查询 `from` 必填 | 400 |
| `size` | 1..100（>100 夹取） | 见 `0.2` |
| `limit`（AI 查询返回条数） | 1..50，默认 20 | 400 |
| 导出最大行数 | 50000 | 422 `EXPORT_TOO_LARGE` |

## 2. 表结构逐列

> 7 张表。SQL 唯一副本：`app/src/main/resources/db/migration/V1__init.sql`（由本节逐列生成）。
> 不使用任何 Postgres 扩展；所有约束在 DDL 内显式命名（`ck_*` / `uq_*` / `idx_*`）。

### 2.1 `alerts_active`（活动告警，聚合体）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `uuid` | 否 | — | PK |
| `fingerprint` | `char(64)` | 否 | — | `uq_alerts_active_fingerprint`，见 §1.5 |
| `source` | `varchar(128)` | 否 | — | 来源系统 |
| `type` | `varchar(128)` | 否 | — | 故障类型 |
| `resource_id` | `varchar(128)` | 否 | — | 资源标识 |
| `event_code` | `varchar(128)` | 否 | `''` | **归一化空串**，不存 NULL（否则唯一性失效） |
| `severity` | `varchar(16)` | 否 | `'MEDIUM'` | `ck_alerts_active_severity` |
| `severity_rank` | `smallint` | 否 | 生成列 | `GENERATED ALWAYS AS (CASE severity ... END) STORED` |
| `message` | `text` | 否 | — | `ck_alerts_active_message_len` ≤4000 |
| `first_seen_at` | `timestamptz` | 否 | — | 首次上报 |
| `last_seen_at` | `timestamptz` | 否 | — | 最近上报（只前进） |
| `occurrence_count` | `integer` | 否 | `1` | `ck_alerts_active_count` ≥1 |
| `latest_payload` | `jsonb` | 否 | `'{}'` | 最新一条原始 payload |
| `latest_event_id` | `uuid` | 否 | — | 指向 `alert_event.id`（同事务写入） |
| `created_at` | `timestamptz` | 否 | `now()` | |
| `updated_at` | `timestamptz` | 否 | `now()` | upsert 时刷新 |

索引：

| 名称 | 列 | 用途 |
|---|---|---|
| `uq_alerts_active_fingerprint` | `(fingerprint)` UNIQUE | 去重兜底（I-D2） |
| `idx_alerts_active_sort` | `(severity_rank DESC, last_seen_at DESC, id)` | 默认列表排序 |
| `idx_alerts_active_source` | `(source, last_seen_at DESC)` | 按来源筛选 |
| `idx_alerts_active_lastseen` | `(last_seen_at DESC)` | 时间范围筛选 |

### 2.2 `alerts_history`（历史告警）

列 = `alerts_active` 全部列（`severity_rank` 同为生成列），另加：

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `cleared_at` | `timestamptz` | 否 | — | 清除时间 |
| `cleared_by` | `varchar(128)` | 否 | — | 用户名；系统为 `system` |
| `cleared_reason` | `varchar(500)` | 是 | `null` | `ck_alerts_history_reason_len` ≤500 |
| `clear_mode` | `varchar(16)` | 否 | `'MANUAL'` | `ck_alerts_history_mode` ∈ {MANUAL,SYSTEM} |

索引：

| 名称 | 列 | 用途 |
|---|---|---|
| `pk_alerts_history` | `(id)` | 主键（幂等靠它，I-M3） |
| `idx_alerts_history_cleared` | `(cleared_at DESC, id)` | 默认倒序与分页 |
| `idx_alerts_history_cleared_by` | `(cleared_by, cleared_at DESC)` | 按处理人筛选 |
| `idx_alerts_history_source` | `(source, cleared_at DESC)` | 按来源筛选 |
| `idx_alerts_history_fingerprint` | `(fingerprint)` | 复盘同故障历史 |

> 关键字搜索：本期用 `message ILIKE '%kw%'`（10 万行实测；若 >1s 再评估 `pg_trgm`，属 DDL 变更需 ADR）。

### 2.3 `alert_event`（每次上报原文，证据链）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `uuid` | 否 | — | PK；AI 证据引用它 |
| `alert_id` | `uuid` | 否 | — | 所属告警（不设 FK：告警会在两张表间移动） |
| `occurred_at` | `timestamptz` | 否 | — | 上报方声明的时间 |
| `received_at` | `timestamptz` | 否 | `now()` | 服务端接收时间 |
| `severity` | `varchar(16)` | 否 | — | 本次上报的级别（未合并前） |
| `message` | `text` | 否 | — | 本次上报描述 |
| `payload` | `jsonb` | 否 | `'{}'` | 本次原始 payload |
| `ingress` | `varchar(8)` | 否 | — | `ck_alert_event_ingress` ∈ {HTTP,KAFKA} |

索引：`idx_alert_event_alert (alert_id, occurred_at DESC, id)`、`idx_alert_event_received (received_at)`。

保留策略：每次写入后，同事务裁剪——同一 `alert_id` 只保留最近 **50** 条（按 `occurred_at DESC, id DESC`）。理由：AI/详情只需可点开的近因事件，避免无界增长。

### 2.4 `audit_event`（审计事实表）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `uuid` | 否 | — | PK |
| `occurred_at` | `timestamptz` | 否 | — | 业务发生时间（混排排序键） |
| `recorded_at` | `timestamptz` | 否 | `now()` | 落库时间（排查延迟用） |
| `actor` | `varchar(128)` | 否 | — | 用户名；系统为 `system`；服务为 client_id |
| `actor_type` | `varchar(16)` | 否 | — | `ck_audit_event_actor_type` ∈ {USER,SYSTEM,SERVICE} |
| `action` | `varchar(32)` | 否 | — | `ck_audit_event_action`，见 §1.3 |
| `target_type` | `varchar(16)` | 否 | — | `ck_audit_event_target_type` ∈ {ALERT,AUTH,ENDPOINT} |
| `target_id` | `varchar(128)` | 是 | `null` | 告警 UUID / `obj:act` |
| `outcome` | `varchar(16)` | 否 | — | `ck_audit_event_outcome` ∈ {SUCCESS,DENIED,FAILED} |
| `source` | `varchar(16)` | 否 | — | `ck_audit_event_source` ∈ {APP,KEYCLOAK} |
| `source_event_id` | `varchar(128)` | 是 | `null` | Keycloak 事件 id；`uq_audit_event_source_event`（部分唯一，WHERE not null） |
| `client_net` | `varchar(64)` | 是 | `null` | 脱敏网段，见 §6.2 |
| `trace_id` | `varchar(64)` | 是 | `null` | 与响应 envelope 的 `traceId` 一致 |
| `detail` | `jsonb` | 否 | `'{}'` | 白名单字段，见 §6.1 |

索引：

| 名称 | 列 | 用途 |
|---|---|---|
| `idx_audit_event_time` | `(occurred_at DESC, id)` | 默认倒序 / 时间范围（NFR-04） |
| `idx_audit_event_actor` | `(actor, occurred_at DESC)` | 按人筛选 |
| `idx_audit_event_action` | `(action, occurred_at DESC)` | 按动作筛选 |
| `idx_audit_event_outcome` | `(outcome, occurred_at DESC)` | 按结果筛选 |
| `uq_audit_event_source_event` | `(source_event_id) WHERE source_event_id IS NOT NULL` | 采集幂等（I-C3） |

### 2.5 `processed_event`（Kafka 消费幂等）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `consumer_group` | `varchar(128)` | 否 | — | 消费组 |
| `topic` | `varchar(255)` | 否 | — | topic |
| `partition_no` | `integer` | 否 | — | 分区号（避免与 `partition` 关键字冲突） |
| `record_offset` | `bigint` | 否 | — | 偏移（避免与 `offset` 关键字冲突） |
| `processed_at` | `timestamptz` | 否 | `now()` | |

主键：`pk_processed_event (consumer_group, topic, partition_no, record_offset)`。
清理：本期不清理（演示数据量小）；二期按 `processed_at` 归档。

### 2.6 `keycloak_cursor`（采集游标）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `varchar(64)` | 否 | — | 固定 `realm-events` |
| `last_event_time` | `bigint` | 否 | `0` | epoch millis |
| `last_event_id` | `varchar(128)` | 是 | `null` | 同毫秒事件的辅助游标 |
| `updated_at` | `timestamptz` | 否 | `now()` | |

### 2.7 `notification`（下游通知落点）

| 列 | 类型 | 可空 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `uuid` | 否 | — | PK |
| `alert_id` | `uuid` | 否 | — | 来源告警 |
| `channel` | `varchar(16)` | 否 | `'LOG'` | `ck_notification_channel` |
| `payload` | `jsonb` | 否 | `'{}'` | 通知内容（清除事件副本） |
| `created_at` | `timestamptz` | 否 | `now()` | |

索引：`idx_notification_alert (alert_id)`、`idx_notification_created (created_at DESC)`。

## 3. 路径契约

> 编号与 `02-design.md §3` 一致。每条给出：权限、入参、返回、错误。
> 公共规则：见 §0.2（分页）、§0.3（错误）、§0.4（空值）；公共错误码见 §4。

### 3.0 视图对象（共用返回结构）

**`AlertView`**（#1）
`id, severity, source, type, resourceId, eventCode, message, firstSeenAt, lastSeenAt, occurrenceCount, fingerprint`

**`AlertHistoryView`**（#2）= `AlertView` + `clearedAt, clearedBy, clearedReason, clearMode`

**`AlertDetailView`**（#3）
`id, status(ACTIVE|CLEARED), severity, source, type, resourceId, eventCode, message, firstSeenAt, lastSeenAt, occurrenceCount, fingerprint, mergeFields{source,type,resourceId,eventCode}, mergeRule, latestPayload, events[AlertEventView], cleared(nullable)`

**`AlertEventView`**（#3 `events[]`）
`id, occurredAt, severity, message, ingress`（`payload` 不进列表，避免响应膨胀；点开单条事件的全文在二期）

**`AuditEventView`**（#6）
`id, occurredAt, actor, actorType, action, targetType, targetId, outcome, source, clientNet, traceId, detail`

**`PageView<T>`**
`items[], total, page, size`

### #1 `GET /api/alerts/active`

权限 `alert:read`。查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `severity` | string，可重复 | 否 | 多值取并集 |
| `source` | string，可重复 | 否 | 多值取并集 |
| `from` / `to` | ISO-8601 | 否 | 作用于 `lastSeenAt` |
| `page` / `size` | int | 否 | §0.2 |

返回 `200`：

```json
{ "items": [ {
    "id": "…uuid…", "severity": "CRITICAL", "source": "payment-api", "type": "5xx",
    "resourceId": "pay-01", "eventCode": "RULE_503", "message": "…",
    "firstSeenAt": "2026-11-23T02:00:00Z", "lastSeenAt": "2026-11-23T02:05:00Z",
    "occurrenceCount": 12, "fingerprint": "…64 hex…"
  } ], "total": 1, "page": 0, "size": 20 }
```

错误：400（参数非法）。

### #2 `GET /api/alerts/history`

权限 `alert:read`。查询参数：`severity[]`、`source[]`、`clearedBy`、`keyword`、`from`/`to`（作用于 `clearedAt`）、`page`/`size`。

返回 `200`：`items[]` 为 `AlertHistoryView`（§3.0 字段 + `clearedAt` / `clearedBy` / `clearedReason` / `clearMode`）。
错误：400。**不提供导出**（导出只在审计 `#7`）。

### #3 `GET /api/alerts/{id}`

权限 `alert:read`。路径参数 `id`（UUID，非法 UUID → 404）。

返回 `200`（活动或历史都能查，`status` 区分）：

```json
{
  "id": "…", "status": "ACTIVE",
  "severity": "CRITICAL", "source": "payment-api", "type": "5xx",
  "resourceId": "pay-01", "eventCode": "RULE_503",
  "message": "…", "firstSeenAt": "…", "lastSeenAt": "…", "occurrenceCount": 12,
  "fingerprint": "…",
  "mergeFields": { "source": "payment-api", "type": "5xx", "resourceId": "pay-01", "eventCode": "RULE_503" },
  "mergeRule": "source+type+resourceId+eventCode（忽略大小写与首尾空格）",
  "latestPayload": { },
  "events": [ { "id": "…", "occurredAt": "…", "severity": "HIGH", "message": "…", "ingress": "HTTP" } ],
  "cleared": null
}
```

- `events`：最近 20 条（倒序），供 US-B4「原始信息」与 US-D1 证据点开。
- `status='CLEARED'` 时 `cleared = {clearedAt, clearedBy, clearedReason, clearMode}`，且不再有 `latestPayload` 之外的活动语义。
- 错误：404 `ALERT_NOT_FOUND`。

### #4 `POST /api/alerts/{id}/clear`

权限 `alert:clear`。请求体：

```json
{ "reason": "已重启支付网关，观察 5 分钟无复发" }
```

`reason` 可选（0..500）；请求体可为空对象或省略。

返回 `200`：`{ "id": "…", "clearedAt": "…", "clearedBy": "zhangwei" }`。
错误：`404 ALERT_NOT_FOUND`、`409 ALREADY_CLEARED`、`400 VALIDATION_FAILED`、`403 ACCESS_DENIED`（并写审计 `DENIED`）。

### #5 `POST /api/alerts/ingest`

权限 `alert:ingest`。请求体：

```json
{
  "source": "payment-api", "type": "5xx", "resourceId": "pay-01", "eventCode": "RULE_503",
  "severity": "CRITICAL", "message": "HTTP 503 连续 12 次",
  "occurredAt": "2026-11-23T02:00:00Z",
  "payload": { "rule": "RULE_503", "count": 12 }
}
```

- `eventCode`、`payload` 可选；`fingerprint` 字段**不接受**（§1.5）。
- 返回 `202 Accepted`：`{ "id": "…", "created": true, "occurrenceCount": 1, "severity": "CRITICAL", "lastSeenAt": "…" }`
  - `created=false` 表示命中去重、并入已有行。
- 错误：`400 VALIDATION_FAILED`（缺 `source`/`type` 等）、`401`、`403`。

### #6 `GET /api/audit/events`

权限 `audit:read`。查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `from` / `to` | ISO-8601 | `from` 必填 | 作用于 `occurredAt`；跨度 ≤90 天（NFR-04 前提） |
| `actor` | string | 否 | 精确匹配 |
| `action` | string，可重复 | 否 | 多值并集 |
| `outcome` | string，可重复 | 否 | 多值并集 |
| `page` / `size` | int | 否 | §0.2 |

返回 `200`：

```json
{
  "items": [ {
    "id": "…", "occurredAt": "2026-11-23T02:10:00Z", "actor": "zhangwei", "actorType": "USER",
    "action": "ALERT_CLEARED", "targetType": "ALERT", "targetId": "…uuid…",
    "outcome": "SUCCESS", "source": "APP", "clientNet": "10.20.3.0/24", "traceId": "…",
    "detail": { "path": "/api/alerts/{id}/clear", "method": "POST",
                "alert": { "severity": "CRITICAL", "source": "payment-api", "type": "5xx",
                           "resourceId": "pay-01", "eventCode": "RULE_503",
                           "occurrenceCount": 12, "messageExcerpt": "HTTP 503 连续 12 次" } }
  } ],
  "total": 1, "page": 0, "size": 20,
  "eventsCollected": true, "eventsDisabled": false, "collectorLastRunAt": "2026-11-23T02:10:05Z"
}
```

- `eventsCollected` / `eventsDisabled` / `collectorLastRunAt`：供 US-C1 的「未采集到数据」提示；三个字段**恒存在**。
- 401 不写审计；403 的 `ACCESS_DENIED` 行由 `JsonAccessDeniedHandler` 写入。

### #7 `GET /api/audit/events/export`

权限 `audit:export`。查询参数同 `#6`（`from` 必填、跨度 ≤90 天），另加 `format=csv`（唯一支持值，缺省即 csv）。

返回 `200`：`Content-Type: text/csv; charset=utf-8`，带 UTF-8 BOM；`Content-Disposition: attachment; filename="audit-{from}-{to}.csv"`。

列顺序（固定，表头中文）：

```
发生时间,操作人,操作人类型,动作,对象类型,对象,结果,来源,客户端网段,详情摘要
```

- `详情摘要` 为 `detail` 的**单行压缩文本**（`key=value` 用 `; ` 连接），不含换行、不含敏感字段。
- `occurredAt` 用 ISO-8601 UTC。
- 行数上限 50000 → 超限 422 `EXPORT_TOO_LARGE`（提示缩小时间范围）；不允许静默截断。

### #8 `POST /api/ai/briefing/{id}`

权限 `ai:briefing`。路径 `id` 为告警 UUID（活动或历史均可）；请求体可为空。

响应 `200`：`Content-Type: text/event-stream; charset=utf-8`，`Cache-Control: no-cache`，`X-Accel-Buffering: no`。

事件序列：

| event | data | 说明 |
|---|---|---|
| `delta` | `{"text":"…"}` | 逐字/逐段，顺序拼接即全文 |
| `done` | `{"briefing":{…},"degraded":false,"model":"…","promptVersion":"briefing-v1"}` | 完整结构化结果（`degraded=true` 时 `briefing` 退化为 `{"summary":"纯文本"}`） |
| `error` | `{"code":"AI_UNAVAILABLE","message":"…","traceId":"…"}` | 终止事件；之后连接关闭 |

`briefing` 结构（非降级时）：

```json
{
  "impact":    [ { "text": "…", "confidence": 0.8, "evidenceEventIds": ["…uuid…"] } ],
  "hypotheses":[ { "text": "…", "confidence": 0.6, "evidenceEventIds": ["…"] } ],
  "actions":   [ { "text": "…", "priority": "HIGH", "evidenceEventIds": ["…"] } ]
}
```

- `confidence` ∈ [0,1]；`evidenceEventIds` 必须是本次输入事件集合的子集（否则后端丢弃该条并记录 `guardrail=evidence_dropped`）。
- 首字节 < 2s（US-D1）；模型不可达 → 在 SSE 内发 `error` 事件，且 HTTP 状态已是 200（流已开始；若在首字节前失败则返回 `503` + 普通 JSON envelope）。
- 404 `ALERT_NOT_FOUND`；403。

### #9 `POST /api/ai/query`

权限 `ai:query`。请求体：

```json
{ "question": "昨天下午有没有支付相关的严重告警", "limit": 20 }
```

返回 `200`：

```json
{
  "filter": { "scope": "active", "from": "2026-11-22T04:00:00Z", "to": "2026-11-22T10:00:00Z",
              "severities": ["CRITICAL"], "sources": [], "keyword": "支付", "clearedBy": null },
  "items": [ /* AlertView 或 AlertHistoryView，取决于 scope */ ],
  "total": 3, "page": 0, "size": 20,
  "explanation": "按 2026-11-22 12:00–18:00（Asia/Shanghai）查询 CRITICAL 且描述含「支付」的活动告警",
  "droppedFields": []
}
```

- `filter` 只允许这些键：`scope(active|history)`、`from`、`to`、`severities`、`sources`、`keyword`、`clearedBy`。**其他键一律拒绝**，写进 `droppedFields` 并在 422 时说明。
- `filter` 必须原样展示给用户（US-D2）；`explanation` 用业务时区描述范围。
- 错误：`422 AI_UNPARSEABLE`（超出能力，`message` 要列出可查字段）、`503 AI_UNAVAILABLE`、`400 VALIDATION_FAILED`（`question` 长度）、403。
- **绝不生成 SQL**：模型只输出上述 JSON，由后端映射到既有的 `AlertReadPort.query(...)`。

### #10 `GET /api/ai/digest`（二期，契约位保留）

本期**不实现**：请求返回 `404 NOT_FOUND`。二期返回 `{ "summaryMarkdown": "…", "citations": ["<auditEventId>"], "generatedAt": "…", "degraded": false }`，每条摘要都必须能指到真实审计 id。

### #11 `GET /api/whoami`

权限 `whoami:read`（只要认证通过即满足，用于前端渲染与 T02 验收）。

返回 `200`：

```json
{
  "username": "zhangwei",
  "subject": "b1f2…",
  "roles": ["operator"],
  "permissions": [ { "obj": "alert", "act": "read" }, { "obj": "alert", "act": "clear" } ]
}
```

- `roles`：只含 `02-design.md §4.3` 定义的受管角色；空数组表示无权限。
- `permissions`：按当前角色对 `§5.1` 全量 `obj×act` 求值后的允许集合，前端据此禁用按钮（US-A3），**不在前端判断角色名**。
- 无 token → `401 UNAUTHORIZED`（由 entry point 输出）。

### #12 `GET /api/_perm/check`（调试端点，仅非 prod）

开关 `app.security.test-endpoints=true` 且 profile ≠ prod；prod 下该路径不存在（404）。

参数：`obj`（必填，§5.1 五值之一）、`act`（必填，八值之一）。

返回 `200`：`{ "allowed": true, "roles": ["operator"], "obj": "alert", "act": "clear" }`
错误：400（未知 obj/act）、401。

## 4. 错误码

| `code` | HTTP | 触发场景 | `message` 示例 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 必填缺失、超长、枚举非法、时间跨度超限、未知排序参数 | `字段 type 不能为空` |
| `UNAUTHORIZED` | 401 | 无/过期/非法 token | `未认证或凭证已失效` |
| `ACCESS_DENIED` | 403 | 权限不足；或 handler 未标注 `@RequirePermission`（默认拒绝） | `无权执行 alert:clear` |
| `ALERT_NOT_FOUND` | 404 | 告警 id 不存在（活动与历史都没有） | `告警不存在` |
| `NOT_FOUND` | 404 | 路径不存在 / 二期未实现端点 | `资源不存在` |
| `ALREADY_CLEARED` | 409 | 重复清除 | `告警已被清除` |
| `EXPORT_TOO_LARGE` | 422 | 导出行数 > 50000 | `结果超过 50000 行，请缩小时间范围` |
| `AI_UNPARSEABLE` | 422 | 自然语言超出白名单能力 | `只能查：时间范围、严重级别、来源、关键字、处理人` |
| `AI_UNAVAILABLE` | 503 | 模型不可达/超时/熔断 | `AI 服务暂不可用，请稍后重试` |
| `INTERNAL_ERROR` | 500 | 未预期异常（不暴露细节） | `服务内部错误` |
| `METHOD_NOT_ALLOWED` | 405 | 方法不对 | `请求方法不被支持` |

约定：新增错误码必须先改本表；`code` 一旦发布不改名（前端与测试依赖它）。

## 5. Casbin 字典与策略

### 5.1 obj / act 字典

| `obj` | 含义 | 允许的 `act` |
|---|---|---|
| `alert` | 告警资源 | `read`、`ingest`、`clear` |
| `audit` | 审计资源 | `read`、`export` |
| `ai` | AI 能力 | `briefing`、`query`、`digest`（二期） |
| `whoami` | 当前身份 | `read` |
| `_perm` | 权限调试端点 | `check` |

### 5.2 `model.conf`

```
[request_definition]
r = sub, obj, act

[policy_definition]
p = sub, obj, act

[policy_effect]
e = some(where (p.eft == allow))

[matchers]
m = r.sub == p.sub && r.obj == p.obj && r.act == p.act
```

### 5.3 `policy.csv`（完整，逐行）

```csv
p, admin,    alert,  read
p, admin,    alert,  clear
p, admin,    alert,  ingest
p, admin,    audit,  read
p, admin,    audit,  export
p, admin,    ai,     briefing
p, admin,    ai,     query
p, admin,    whoami, read
p, admin,    _perm,  check
p, operator, alert,  read
p, operator, alert,  clear
p, operator, audit,  read
p, operator, ai,     briefing
p, operator, ai,     query
p, operator, whoami, read
p, viewer,   alert,  read
p, viewer,   audit,  read
p, viewer,   ai,     briefing
p, viewer,   ai,     query
p, viewer,   whoami, read
p, service,  alert,  ingest
```

- `service` 角色供设备/外部系统用：Keycloak confidential client `ingest-service` 的 service account 持有它。
- `viewer` 无 `alert:clear`、无 `audit:export`（US-A3/US-C4 的最小权限解释）。
- 演示「改配置即改行为」（US-A5）：给 `viewer` 加 `p, viewer, alert, clear` 后重启，`#12` 由 `false` 变 `true`，`#4` 由 403 变 200。

## 6. 脱敏与白名单

### 6.1 `audit_event.detail` 允许的键（白名单）

| 键 | 类型 | 出现场景 | 内容约束 |
|---|---|---|---|
| `path` | string | APP 路径 | **模板化路径**（`/api/alerts/{id}/clear`），不含真实 id |
| `method` | string | APP 路径 | `GET/POST/...` |
| `reason` | string | 清除 | 只存**长度**（如 `{"reasonLength":24}`），不存原文 |
| `errorClass` | string | FAILED | 异常类名，不含 message/堆栈 |
| `alert` | object | ALERT_CLEARED | `severity, source, type, resourceId, eventCode, occurrenceCount, messageExcerpt` |
| `messageExcerpt` | string | 同上 | `message` 前 120 字，**去换行** |
| `clientId` | string | KEYCLOAK LOGIN | Keycloak 事件里的 `clientId` |
| `error` | string | KEYCLOAK LOGIN_FAILED | Keycloak 事件 `error`（如 `invalid_user_credentials`） |
| `eventType` | string | KEYCLOAK | 原始 `type` |
| `requestedObj` / `requestedAct` | string | ACCESS_DENIED | 越权时请求的 `obj`/`act` |
| `guardrail` | string | AI | 如 `evidence_dropped`（只记事实，不记模型原文） |

**禁止出现的键**：`token`、`access_token`、`refresh_token`、`authorization`、`password`、`secret`、`body`、`payload`、`requestBody`、`ip`（完整）、`headers`。
写审计前必须过一遍 `AuditDetailSanitizer`（键名黑名单 + 值长度上限 500 + 去换行）；单测覆盖黑名单命中即剔除（US-C5）。

### 6.2 IP / 网段脱敏

| 输入 | 输出 |
|---|---|
| `10.20.3.77` | `10.20.3.0/24` |
| `192.168.100.5` | `192.168.100.0/24` |
| `2001:db8:abcd:12::1` | `2001:db8:abcd:12::/64` |
| `null` / 空 | `null` |
| 非法格式 | `null`（丢弃，不落库、不记原值） |

- 取 IP 的顺序：`X-Forwarded-For` 第一段（若配置信任代理）→ `request.getRemoteAddr()`。
- **日志里永远不打印完整 IP**；只允许打印脱敏后的网段，或干脆不打印。
- 单测：4 类输入各一条，外加「detail 中不存在原始 IP 字符串」。

### 6.3 Kafka 消息体

**`AlertIngestedMessage`**（topic `alert.ingested`）

```json
{ "source":"payment-api", "type":"5xx", "resourceId":"pay-01", "eventCode":"RULE_503",
  "severity":"CRITICAL", "message":"HTTP 503 连续 12 次",
  "occurredAt":"2026-11-23T02:00:00Z", "payload":{ } }
```

- 与 `#5` 请求体字段**完全一致**（同一个 `IngestCommand`）；`key` = fingerprint。
- 非法 JSON / 字段非法 → 重试 2 次后进 `alert.ingested.dlt`（原消息体保留，便于排查）。

**`AlertClearedMessage`**（topic `alert.cleared`）

```json
{ "alertId":"…uuid…", "fingerprint":"…", "severity":"CRITICAL", "source":"payment-api",
  "type":"5xx", "resourceId":"pay-01", "occurrenceCount":12,
  "clearedAt":"2026-11-23T02:10:00Z", "clearedBy":"zhangwei" }
```

- `key` = `alertId`；只在主事务提交后发出（§5.3）。
- notify 消费后写 `notification` 一行（`channel=LOG`、`payload` = 本消息体副本）。

### 6.4 日志白名单

允许：`action`、`alertId`、`actor`、`outcome`、`code`、`traceId`、`topic`、`partition`、`offset`、`count`、`durationMs`、`clientNet`（脱敏后）。
禁止：token、Authorization 头、请求体/响应体、密码、完整 IP、SQL 参数值、模型 prompt 全文。
统一格式：`log.info("action={} alertId={} traceId={}", ...)`。

## 7. 前端类型生成与契约校验

### 7.1 生成命令（`openapi-typescript` 已在依赖白名单）

从 `frontend/` 执行：

```bash
npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts
```

- 生成物**提交进仓库**，`npm run typecheck` 才能独立于后端通过。
- 手写接口类型 = 违规（AGENTS §5）；`any` / `@ts-ignore` = 违规（AGENTS §2.1.11）。
- 运行期对齐（额外保险）：T13 用 `curl -s localhost:8080/v3/api-docs > /tmp/api-docs.json`，与 `docs/api/openapi.yaml` 对比路径集合与字段名集合，差异必须为空或已在 `§8 变更记录` 说明。

### 7.2 `openapi.yaml` 的地位

- 它是 `§3` 的机器可读镜像；**语义以本文件 §3 为准**，`openapi.yaml` 只做类型与 mock 载体。
- 修改顺序：本文件 §3 → `openapi.yaml` → 重新生成 `schema.d.ts` → 前端编译。

## 8. 变更记录

| 版本 | 日期 | 变更 | 关联 ADR |
|---|---|---|---|
| v1.0 | 2026-11-23 | 首版：7 表逐列、12 路径、11 错误码、Casbin 21 行策略、fingerprint 规范、脱敏白名单 | 0001–0010 |

> 后续任何字段/枚举/错误码变更都必须在 `01` 中留 **ADR**，并在本表追加一行；否则视为不合格产出。

