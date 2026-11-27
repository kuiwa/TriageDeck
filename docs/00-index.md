# 文档地图（00-index）

> 本文件是 `docs/` 的入口。**阅读顺序 = 下表顺序**。
> 冲突时的效力：`AGENTS.md` > `02b-contract.md`（字段级唯一真相）> 各册 LLD > `02-design.md`（总览）> `01-requirements.md`（需求原文，不改）。
> 任一文件右上角的「状态」决定能不能据此写代码：只有 `冻结` 的才能作为实现依据。

## 1. 索引

| 顺序 | 文件 | 作用 | 状态 | 主要读者 |
|---|---|---|---|---|
| 0 | `AGENTS.md`（仓库根） | 硬约束、依赖白名单、代码约定 | 冻结 | 人和模型 |
| 1 | `docs/01-requirements.md` | 需求原文（US-A1…US-D4），唯一验收依据 | 冻结（不改原文） | 全体 |
| 2 | `docs/03-scope-v1.md` | 本期范围冻结：20 条必做 + 3 条可选的 In/Out、二期清单、需求勘误 | v1.0 待评审 | 全体 |
| 3 | `docs/02-design.md` | 总体设计：系统全景、数据模型不变量、端点总表、授权/消息/审计/认证/AI 模型、非功能落点 | v1.0 待评审 | 全体 |
| 4 | `docs/02b-contract.md` | **字段级唯一真相**：枚举、错误码、限制表、7 张表逐列、12 条路径入参/返回、Casbin 字典、脱敏规则 | v1.0 待评审 | 后端/前端 |
| 4.5 | `docs/04-review-checklist.md` | 15 个全局决策 + 5 册 LLD 的 20 条待确认项 + 评审顺序 + 否决影响面 | 待评审 | 评审人 |
| 5 | `docs/lld/00-lld-template.md` | LLD 统一 9 节模板与评审清单 | 冻结 | 写文档的人 |
| 6 | `docs/lld/01-auth.md` | 认证与授权（US-A1…A5，T02/T03） | v0.9 待评审 | 后端/前端 |
| 7 | `docs/lld/02-alert.md` | 告警摄入/去重/列表/详情/清除/历史（US-B1…B7，T04–T07） | v0.9 待评审 | 后端/前端 |
| 8 | `docs/lld/03-audit.md` | 审计写入/采集/查询/导出（US-C1…C6，T08/T09） | v0.9 待评审 | 后端/前端 |
| 9 | `docs/lld/04-ai.md` | AI 简报/自然语言查询（US-D1…D4，T11/T12） | v0.9 待评审 | 后端/前端 |
| 10 | `docs/lld/05-platform-notify.md` | 平台横切 + Kafka 两链路 + 幂等 + DLT + 通知（T10） | v0.9 待评审 | 后端 |
| 11 | `docs/db/schema-v1.md` | 人读版 ER、索引、不变量（SQL 只有一份，在 `app/src/main/resources/db/migration/`） | v0.9 待评审 | 后端/DBA |
| 12 | `docs/api/openapi.yaml` | 接口契约机器可读版；前端类型由它生成 | v0.9 待评审 | 前端/后端 |
| 13 | `docs/ai-tasks.md` | 施工任务 T01…T13（白名单、验收、禁止项） | v1.1 已按本套文档校准 | 模型 |
| 14 | `docs/adr/` | 决策记录（0001…0010） | v1.0 待评审 | 全体 |
| 15 | `docs/traceability.md` | 追溯矩阵（US → 章节 → 端点 → 表 → 测试 → T → 状态） | 已填充（T13 更新测试列） | 全体 |
| 16 | `docs/ai-log/Txx.md` | 每个任务的输入/返工/人工改动三行记录 | 随任务产生 | 全体 |
| 17 | `docs/session-sync-recovery.md` | Copilot Chat 本地会话存储与 Rebuild 后恢复排查记录（非持久性保证） | 运维记录 | 开发者 |

## 2. 本套文档的修订原则

1. **`02b-contract.md` 是字段、枚举、错误码、状态的唯一真相。** 其他文档一律引用小节号，不复制字段定义。
2. `02-design.md §2` 只描述**不变量**（例如「活动表 fingerprint 唯一」「移动必须在同一事务」），不列列名与类型。
3. 改契约 → 先提 ADR → 同步改 `02b-contract.md` → 再改 LLD → 最后改代码。改动未经 ADR 视为不合格。
4. 每册 LLD 末尾有「待确认项」表；评审通过后整行删除，并把结论写进对应小节。
5. 文档里不允许出现「待定/TODO」进入 `冻结` 状态。

## 3. 命名与路径约定（本仓库）

| 项 | 约定 |
|---|---|
| 文档目录 | 全部在 `docs/`；需求与任务书不在仓库根（根只留 `AGENTS.md`、`README.md`） |
| 数据库迁移 | 唯一副本：`app/src/main/resources/db/migration/V1__init.sql`（Flyway 默认路径） |
| 人读数据库说明 | `docs/db/schema-v1.md` |
| 接口契约 | `docs/api/openapi.yaml`；前端类型命令见 `02b-contract.md §7` |
| Java 包根 | `io.github.wangkui.triagedeck.<module>.<layer>` |
| Gradle 模块 | `:platform` `:security` `:alert` `:audit` `:ai` `:notify` `:app` |
| 前端 | `frontend/`（独立 npm 工程，构建产物拷贝进 `:app` 的静态资源） |
