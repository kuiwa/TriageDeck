# ADR-0008 AI 只读端口、SSE 与 mock 降级

- 状态：接受
- 日期：2026-11-23
- 影响：`ai`、`alert`（端口）、`02-design.md §8`、`lld/04`

## 背景

US-D1 要流式简报且证据可查；US-D2 要把自然语言映射成受控筛选；US-D4 要求 AI 绝不改状态，
并且模型不可用时不影响其余功能；NFR-08 还要求离线可演示。

## 决策

1. **只读端口**：`ai` 只依赖 `alert.api.port.AlertReadPort`（`findDetail` / `query`），ArchUnit 断言 `ai` 不碰 `alert` 的 service/domain/repo，且 `ai` 包内无 Repository、无写方法。
2. **SSE**：`text/event-stream` + `delta/done/error` 事件；前端用 `fetch` 读流（`EventSource` 不支持 POST 与 Authorization 头）。
3. **结构化 + 降级**：模型输出 JSON → 后端 schema 校验 → 一次修复重试 → 仍失败降级纯文本 `degraded=true`。
4. **证据守卫**：`evidenceEventIds` 必须是本次输入事件 id 子集，越界条目丢弃（不只记日志）。
5. **数据即数据**：告警 message 只出现在 user 消息的 `<<<ALERT_DATA>>>` 块内，system prompt 不含任何外部文本。
6. **mock**：`app.ai.mock=true` 返回固定结构，供离线演示与 e2e。
7. **不可用**：首字节前失败 → 503 `AI_UNAVAILABLE`；`ai.enabled=false` 亦同。

## 理由

1. 「只读端口」把 US-D4 从「靠自觉」变成「编译期不可能」。
2. SSE 是需求明确要求（逐字出现）；POST 决定不能用 EventSource。
3. 降级而不是报错，保证演示不会因为模型抽风中断（NFR-07/08）。

## 后果

- 正：AI 故障隔离；证据可点开；注入文本无法变成指令。
- 负：生成阶段前端看到的是原始 JSON 文本流（已在 `lld/04 附录 A1` 记录，二期改双段）。
- 约束：`ai` 每次调用必须设超时；SSE 线程不得持有数据库事务。

## 替代方案

- AI 直接调 `alert` 的 service：违反依赖与 US-D4。
- 一次性返回（不流式）：违反 US-D1。
- 无 mock：断网无法演示，违反 NFR-08。
