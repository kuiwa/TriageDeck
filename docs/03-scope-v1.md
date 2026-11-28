# 范围冻结 v1（03-scope-v1）

> 本文件解决三件事：23 条 US 本期到底做哪些、需求里 5 处措辞冲突怎么落地、以及二期清单。
> **`01-requirements.md` 原文一个字不改**，所有纠偏写在这里。冲突时的实现依据 = 本文件 + `02b-contract.md`。

## 1. 本期范围（In / Out）

| US | 一句话 | 状态 | 承接 T | 说明 |
|---|---|---|---|---|
| US-A1 | 统一登录（Keycloak） | 本期 | T02 | — |
| US-A2 | 登出 | 本期 | T02 | 登出审计记录由 T09 采集补上 |
| US-A3 | 角色差异 | 本期 | T03/T05 | 前端按钮可用性由 `/api/whoami` 的 `permissions` 决定 |
| US-A4 | 后端拦截越权 | 本期 | T03/T08 | 403 + 审计 DENIED |
| US-A5 | 授权可配置 | 本期 | T03 | 「重启生效」，不做热加载 |
| US-B1 | 双入口上报 | 本期 | T04/T10 | HTTP + Kafka |
| US-B2 | 去重聚合 | 本期 | T04 | **不实现 10 分钟时间窗**，见 ADR-0003 |
| US-B3 | 活动列表 | 本期 | T05 | — |
| US-B4 | 告警详情 | 本期 | T05 | 「原始信息」= 最新事件 payload + 最近 50 条事件 |
| US-B5 | 清除移入历史 | 本期 | T06 | — |
| US-B6 | 历史查询 | 本期 | T07 | 关键字搜描述 |
| US-B7 | 自动清除（可选） | **二期** | — | 见 ADR-0009 |
| US-B8 | 级别分布看板 | 本期 | —（增量，未编号） | 复用 `#1`：按 `severity` 各查一次 `size=1` 只读 `total`，前端 10 秒轮询；不改契约 |
| US-C1 | 登录登出记录 | 本期 | T09 | 最长 30 秒延迟，页面必须说明 |
| US-C2 | 清除留痕 | 本期 | T06(注解)/T08(切面) | — |
| US-C3 | 越权留痕 | 本期 | T08 | 写在 `JsonAccessDeniedHandler` |
| US-C4 | 统一时间线 + CSV 导出 | 本期 | T08/T09 | **导出归审计**（`GET /api/audit/events/export`），告警侧不提供 |
| US-C5 | 脱敏 | 本期 | T08 | 单测保证 |
| US-C6 | 完整性校验（可选） | **二期** | — | 见 ADR-0009 |
| US-D1 | 告警简报（SSE） | 本期 | T11 | 证据引用 `alert_event.id` |
| US-D2 | 自然语言查询 | 本期 | T12 | 白名单字段，拒绝未知字段 |
| US-D3 | 交接摘要（可选） | **二期** | — | 端点 `#10` 保留契约位，本期不实现 |
| US-D4 | AI 只建议不动手 | 本期 | T11/T12 | — |

统计：23 条 = 本期 20 + 二期 3。本期 20 条必须全部进 8 分钟剧本。

## 2. 需求勘误表（原文不改，实现以此为准）

| # | 原文位置 | 原文表述 | 冲突点 | 实现依据 |
|---|---|---|---|---|
| E1 | `01-requirements.md` US-B2 / US-B3 | 只写「严重级别」未给词汇 | 无 | `Severity{CRITICAL,HIGH,MEDIUM,LOW,INFO}`，见 `02b-contract.md §1.1` |
| E2 | `ai-tasks.md` T04/T05 验收 | `severity WARN→CRIT`、`severity=CRIT` | 与 `AGENTS.md §5` 枚举冲突 | 一律改为 `HIGH→CRITICAL`、`severity=CRITICAL`；上报不接受 `WARN/CRIT` 别名 |
| E3 | US-B2 | 「10 分钟内重复上报时始终只有一条」 | 10 分钟之后行为未定义 | 活动期内**永久**按 fingerprint 合并；清除后再报 = 新活动行，见 ADR-0003 |
| E4 | US-B1 | 「事件编号（可选）」 | 与 fingerprint 四段、DDL `not null` 冲突 | 缺失或空白 → 归一化为空串参与 fingerprint，见 `02b-contract.md §1.5` |
| E5 | US-C4 | 「可以把当前筛选结果导出 CSV」 | T07 明确告警侧不导出，T09 验收漏了导出 | 导出只归审计：`#7 GET /api/audit/events/export` |
| E6 | US-B4 / US-D1 | 「来源系统的原始信息」「引用具体的上报事件」 | 聚合行只有 `occurrence_count`，取不到第 N 次原文 | 新增 append-only 表 `alert_event`，见 ADR-0006 |
| E7 | US-A3 | 「主管具备值班工程师的全部能力」 | 导出权限归属未定 | `admin` = `operator` 全部 + `audit:export` |
| E8 | US-B7 / US-C6 / US-D3 | 三条「可选」 | 任务书 T01–T13 无对应任务，T13 追溯矩阵会留空 | 二期；追溯矩阵加 `状态` 列（`本期/二期`） |
| E9 | `AGENTS.md §1` | 「11 个接口契约」 | 含调试端点后实际 12 条路径 | `#1…#11` 为业务端点（`#10` 二期未实现），`#12` 为仅 `!prod` 的调试端点 |
| E10 | `AGENTS.md §2.1.2` | 「表结构以 `02-design.md §2` 为准」 | 与任务书「以 `02b-contract.md` 为准」双真相 | 逐列以 `02b-contract.md §2` 为准；`02-design.md §2` 只写不变量 |

## 3. 二期清单（写进 README，不留 TODO 在主干代码里）

| 项 | 内容 | 已有铺垫 |
|---|---|---|
| B7 自动清除 | 定时扫描超时活动告警 → `clear_mode=SYSTEM`、`cleared_by='system'`、`actor_type=SYSTEM` | 表已留 `clear_mode` 列与 `ActorType.SYSTEM` |
| C6 哈希链 | `audit_event.prev_hash` / `hash`，`GET /api/audit/verify` | 表留 `recorded_at` 与主键顺序；校验入口未实现 |
| D3 交接摘要 | `GET /api/ai/digest`，摘要每句带 `citations:[auditEventId]` | 契约位 `#10` 已写、AI 只读端口已含审计读取方法 |
| ACL 精细化 | 从「角色」下沉到「用户 + 资源属主」 | Casbin 模型已是 sub/obj/act 三元，可直接换 sub 来源 |
| 导出异步化 | 超 5 万行改任务队列 + 下载链接 | 导出已按流式写、有行数上限与 422 |
| 审计留存策略 | 按 `occurred_at` 分区/归档 | 索引已按时间列设计 |

## 4. 本期不做（原文 §6 之外的补充）

- 告警 ACK、通知通道（邮件/短信/webhook）、API 网关、Grafana 审计、多租户、K8s：沿用原文 §6 的理由与「以后怎么做」。
- 前端不做「告警侧 CSV 导出」；不做「AI 写操作」；不做「Keycloak 用户同步进 Casbin」。
- 后端不做 session、不做 Basic Auth、不做自制登录表单。
