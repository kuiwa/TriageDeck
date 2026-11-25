# ADR-0005 审计详情由目标模块 SPI 提供（AuditTargetResolver）

- 状态：接受
- 日期：2026-11-23
- 影响：`audit`、`alert`、`platform`、`02-design.md §6.3`

## 背景

US-C2 要求审计记录「能看到告警的关键信息，不用再去点一次告警详情」。
但这些信息（severity/source/type/resourceId/occurrenceCount）属于 `alert` 的领域，
`audit` 不允许 import `alert`；切面也不能在 `alert` 里写审计（T08 禁止项）。

## 决策

`platform.audit` 定义 SPI：

```java
public interface AuditTargetResolver {
    String targetType();                                   // "ALERT"
    Optional<Map<String, Object>> resolve(String targetId); // 只读快照
}
```

- `alert` 实现 `AlertAuditTargetResolver`（`@Component`），按 id 查活动表→历史表，返回白名单字段。
- `audit` 的 `AuditableAspect` 在业务结束（提交或回滚）后按 `targetType` 找 resolver 取快照，交给 `AuditDetailSanitizer`。
- 拿不到快照（回滚/不存在）时 `detail` 退化为 `{path, method, errorClass}`。

## 理由

1. 切面只需要「快照字典」，不需要知道告警的任何类型；SPI 是唯一不引入跨模块 import 的做法。
2. 在**事务之外**解析，才能读到已提交的 `alerts_history`；也避免长事务。
3. 快照走白名单（契约 §6.1），保证 US-C5（不含敏感信息）。

## 后果

- 正：审计与告警可各自演进；新增目标类型只加一个 resolver（当前只有 ALERT，**不提前抽象**）。
- 负：多一次只读查询（清除路径），成本可接受（主键查询）。
- 约束：resolver 必须只读，禁止在其中做任何写操作；返回 Map 的键必须在白名单内。

## 替代方案

- 在 `alert` 里直接写审计：违反「service 不手写 auditRepository.save」。
- 切面从方法参数猜快照：拿不到 occurrenceCount 等聚合字段。
- 事件携带全部字段：事务回滚时事件不发，FAILED 审计仍缺快照（本质一样缺）。
