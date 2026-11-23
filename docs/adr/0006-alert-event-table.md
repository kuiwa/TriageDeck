# ADR-0006 新增 `alert_event` 保存每次上报原文

- 状态：接受
- 日期：2026-11-23
- 影响：DDL（新表）、`lld/02`、`lld/04`、US-B4 / US-D1

## 背景

US-B4 要看到「来源系统的原始信息」；US-D1 要求简报「每条推断都要指出依据（引用具体的上报事件），能点开看原文」。
而 `alerts_active` 只保留最新一次 `message`/`payload` 与一个 `occurrence_count`——合并 50 次后，第 7 次的原文已经不存在。

## 决策

新增 append-only 表 `alert_event`：

- 列：`id, alert_id, occurred_at, received_at, severity, message, payload, ingress`。
- 每次上报写一行（与聚合 upsert 在**同一事务**）。
- 同一 `alert_id` 只保留最近 **50** 条（同事务裁剪）。
- 不设外键（告警会在 `alerts_active` / `alerts_history` 之间移动）。

## 理由

1. 证据链必须指向「具体事件」，只有事件表能给 `evidenceEventIds` 提供稳定 id。
2. 50 条上限 + 同事务裁剪，控制写入放大（每请求 1~2 条额外语句）。
3. 不设 FK 是为了让「移动」不需要级联动作；`alert_id` 是逻辑引用。

## 后果

- 正：详情页可列出近因事件；AI 证据可校验；审计快照也可取 `latest_event_id`。
- 负：写入多两条语句（NFR-03 p95<300ms 仍有余量）；事件不随清除迁移（按 alert_id 仍可查）。
- 明确不做：事件冷存储（二期）、单事件详情端点（二期）。

## 替代方案

- 只存最新 payload：无法满足 US-D1 的可点开证据。
- 把事件塞进 `alerts_active.payload` 数组：行会膨胀、并发更新冲突、无法索引。
