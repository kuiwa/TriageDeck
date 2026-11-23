# 评审清单（04-review-checklist）

> 用途：把「15 个决策 + 5 册 LLD 的待确认项」集中成一张表，供一次性评审。
> 勾选方式：在 `结论` 列写 `采纳 / 否决（改为…）`；否决项按 §4 的影响面矩阵同步改文档。

## 1. 全局决策（已按建议落定，如需推翻请标注）

| # | 决策 | 落定值 | 落点 | 结论 |
|---|---|---|---|---|
| D1 | 文档顺序 | 范围冻结 → 契约薄片 → 5 册 LLD → DDL → OpenAPI → 交叉校验 | `00-index.md` | |
| D2 | DDL 唯一真相 | `02b-contract.md §2`；`02-design.md §2` 只写不变量 | `AGENTS.md §2.1.2` | |
| D3 | Severity 词汇 | `CRITICAL/HIGH/MEDIUM/LOW/INFO`，不接受 `WARN/CRIT` 别名 | 契约 §1.1；`ai-tasks` T04/T05/T12 已改 | |
| D4 | 去重时间窗 | 不实现 10 分钟窗：活动期永久合并，清除后再报为新行 | ADR-0003 | |
| D5 | fingerprint 规范化 | trim + lower + null→`""` + `\u001F` 拼接 + SHA-256 | 契约 §1.5 | |
| D6 | 每事件原文 | 新增 `alert_event`，每告警保留 50 条 | ADR-0006；契约 §2.3 | |
| D7 | notify 落表 | 新增 `notification` 表 | 契约 §2.7；`lld/05` | |
| D8 | 可选需求 | B7 / C6 / D3 移二期，保留列与契约位 | ADR-0009；`03-scope-v1` | |
| D9 | CSV 导出归属 | 审计 `#7 GET /api/audit/events/export`；告警侧不导出 | 契约 §3 #7；`ai-tasks` T09 | |
| D10 | 前端依赖 | 白名单加 `@playwright/test` 与 shadcn 传递依赖；类型由 `docs/api/openapi.yaml` 生成 | `AGENTS.md §4 §5` | |
| D11 | 调试端点 | `#11 whoami` 保留至 T13；`#12 _perm/check` 仅非 prod；共 12 条路径（11 业务 + 1 调试，`#10` 二期） | 契约 §3；`02-design §3` | |
| D12 | 本机环境 | Gradle wrapper 引导、Docker Desktop + compose v2、psql 走 `docker compose exec` | `AGENTS.md §6`；`02-design §9.3`；`ai-tasks` T01 | |
| D13 | 业务时区 | 存储 UTC，业务区 `Asia/Shanghai` 仅用于 NL 解析与 `explanation` | ADR-0010 | |
| D14 | AI 只读端口 | `AlertReadPort{findDetail, query}`，`ai` 只依赖该包 | `lld/04 §3`；ArchUnit §10 | |
| D15 | Keycloak 幂等 | 审计表 `source_event_id` 部分唯一 + 游标 `(time,id)` | 契约 §2.4 §2.6；`lld/03 §3.4` | |

## 2. 五册 LLD 的待确认项汇总（20 条）

### `lld/01-auth`（3 条）

| # | 问题 | 建议 | 结论 |
|---|---|---|---|
| 01-A1 | `whoami.permissions` 是否也返回 `service` 角色的集合？ | 返回（前端不展示 service 用户） | |
| 01-A2 | 是否信任 `X-Forwarded-For`？ | 本期不信任，用 `getRemoteAddr` | |
| 01-A3 | `ingest-service` client secret 注入方式 | `.env` → compose env；与 collector secret 同源 | |

### `lld/02-alert`（4 条）

| # | 问题 | 建议 | 结论 |
|---|---|---|---|
| 02-A1 | `alert_event` 每告警 50 条是否够 AI 证据链 | 够；更早数据二期做冷表 | |
| 02-A2 | 列表 `message` 是否截断 | 不截断，前端 `line-clamp` | |
| 02-A3 | 详情 `events[]` 是否带 `payload` | 不带；二期加单事件端点 | |
| 02-A4 | 历史关键字是否也搜 `resourceId` | 只搜 `message`（按 US-B6 原文） | |

### `lld/03-audit`（4 条）

| # | 问题 | 建议 | 结论 |
|---|---|---|---|
| 03-A1 | 「主业务回滚 → 审计仍在」的失败注入方式 | `alerts_history` 的 `CHECK` 违规模拟，不改生产代码 | |
| 03-A2 | `FAILED` 审计是否带告警快照 | 带不上，退化为 `{path,method,errorClass}` | |
| 03-A3 | 导出是否需要水印/签名 | 本期不做，进二期 | |
| 03-A4 | 审计列表默认时间窗 | 前端默认最近 24h，后端仍校验 `from` 必填 | |

### `lld/04-ai`（5 条）

| # | 问题 | 建议 | 结论 |
|---|---|---|---|
| 04-A1 | 生成阶段显示原始 JSON 文本流是否可接受 | 可接受（US-D1 只要求逐字出现）；二期改双段 | |
| 04-A2 | 修复重试是否复用同一条 SSE | 复用，失败即降级 | |
| 04-A3 | 「支付」这类词走哪个字段 | `keyword` 走 `message ILIKE`；`sources` 只精确匹配 | |
| 04-A4 | `confidence` 展示形式 | 接口 0..1，前端百分比 | |
| 04-A5 | 是否需要 AI 调用审计 | 不需要（US-D4）；成本统计二期 | |

### `lld/05-platform-notify`（4 条）

| # | 问题 | 建议 | 结论 |
|---|---|---|---|
| 05-A1 | `processed_event` 是否清理 | 本期不清理，二期归档 | |
| 05-A2 | DLT 是否需要人工重放入口 | 本期不做，二期脚本 | |
| 05-A3 | notify 只写 DB 是否体现「下游收到」 | 足够（查 `notification`） | |
| 05-A4 | 模拟器形态 | Gradle 模块 `:device-simulator` | |

## 3. 建议的评审顺序（一次 30~40 分钟）

1. `03-scope-v1.md` —— 先确认「本期 19 条 + 二期 3 条」和勘误表 E1…E10 你是否认同。
2. `02-design.md §2 §3 §4` —— 不变量、端点地图、权限矩阵（业务上最可能被挑战的三处）。
3. `02b-contract.md §1 §2` —— 枚举、fingerprint、7 张表逐列（改这里最贵）。
4. 五册 LLD 的 §1 §4 §6 §7（边界、时序、事务、审计点）；§9 测试表扫一眼即可。
5. 最后看 `openapi.yaml`（机器可读，逐字段与 §3 对照）。

## 4. 否决某项建议的影响面矩阵

| 若否决 | 需要同步改 |
|---|---|
| D3（加 `WARN/CRIT` 别名） | 契约 §1.1 §1.6、`lld/02 §2 §9`、`ai-tasks` T04 |
| D4（实现 10 分钟窗） | 契约 §2.1（需加 `window` 逻辑或新列）、ADR-0003 重写、`lld/02 §3.1 §4.1`、`IngestIT` 新用例 |
| D5（改规范化规则） | 契约 §1.5、`FingerprintTest`、已入库 fingerprint 需重算（迁移脚本） |
| D6（去掉 `alert_event`） | 契约 §2.3、`lld/02 §3.1`、`lld/04 §3.1`（证据引用改为「告警 id + 时间」）、US-D1 证据可点开将退化为弱证据 |
| D7（notify 不落表） | 契约 §2.7、`lld/05 §3.2 §9`、`ai-tasks` T10 验收需改写 |
| D8（把 B7/C6/D3 拉回本期） | `03-scope-v1 §1`、`traceability` 状态列、新增任务（T14/T15）、`lld/02 §3.2`（SYSTEM 清除）或 `lld/03 §3/§7`（哈希链） |
| D9（导出放告警侧） | 契约 §3 #2 #7、`lld/02 §5`、`lld/03 §5`、`ai-tasks` T07/T09 |
| D11（`whoami` 只在 T02 用） | 契约 §3 #11 寿命说明、前端按钮权限来源需换方案（不推荐） |
| D13（改业务时区） | ADR-0010、`QueryFilterMapperTest` 断言、`explanation` 文案 |
| D14（AI 直接调 alert service） | `02-design §10` ArchUnit 规则、ADR-0008、`lld/04 §3` |

## 5. 评审通过后的动作

1. 把本表 `结论` 列填完 → 各 LLD 附录 A 整行删除（结论并入正文）。
2. `00-index.md` 状态列把 5 册改为「冻结 v1.0」。
3. 打 tag `docs-v1.0`，之后进入 T01；任何后续契约改动走 ADR。
