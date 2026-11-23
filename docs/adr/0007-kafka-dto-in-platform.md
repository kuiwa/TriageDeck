# ADR-0007 Kafka 事件契约 DTO 归 platform

- 状态：接受
- 日期：2026-11-23
- 影响：`platform`、`notify`、`alert`、`02-design.md §5.2`

## 背景

`alert.cleared` 由 `alert` 生产、`notify` 消费。但依赖图是 `notify → platform`，
`notify` 不能依赖 `alert`（否则 ArchUnit 失败）。

## 决策

`platform.event` 放两个消息契约：

- `AlertIngestedMessage`（与 `#5` 请求体字段一致，即 `IngestCommand` 的扁平化）
- `AlertClearedMessage`（`alertId, fingerprint, severity, source, type, resourceId, occurrenceCount, clearedAt, clearedBy`）

生产者与消费者都只依赖 platform 的 DTO。消费逻辑（写库/调业务）留在各自模块：
`alert` 有自己的 `AlertKafkaConsumerService`，`notify` 有自己的 `NotificationConsumerService`。
platform 只提供幂等台账（`ProcessedEventStore`）与错误处理（`KafkaErrorHandlingConfig`）。

## 理由

1. 消息契约本质是「跨模块的公开 API」，与 `@Auditable` 注解同理，放 platform 正当。
2. 一个 DTO 只有两处使用（生产者 + 消费者），符合「行为重复才抽象」的判据——这是消除跨模块冲突，不是提前设计。
3. 消费逻辑跟随业务归属，避免 platform 长出业务依赖。

## 后果

- 正：依赖图干净；消息字段变更只需改一处 + 契约 §6.3 + ADR。
- 负：消息契约不能复用 `alert` 的 DTO（有轻微重复字段声明），可接受。
- 约束：`AlertIngestedMessage` 必须与 `#5` 请求体字段一致，改一边要改另一边（契约 §6.3 已声明）。

## 替代方案

- DTO 放 `alert`、`notify` 反向依赖：破坏依赖图。
- 用 JSON 字符串不做 DTO：失去编译期检查。
