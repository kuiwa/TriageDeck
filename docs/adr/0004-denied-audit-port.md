# ADR-0004 403 审计经 platform 端口写入，而非跨模块 import

- 状态：接受
- 日期：2026-11-23
- 影响：`security`、`audit`、`platform`、`02-design.md §6.1`

## 背景

US-C3 要求「越权被拒也要留痕」。但拒绝发生在 Spring Security 的过滤器链（`JsonAccessDeniedHandler`），
该 handler 在 `security` 模块；审计写入必须用 `REQUIRES_NEW` 且只有 `audit` 能写 `audit_event`。
`security` 不允许 import `audit`（AGENTS §2.3 / ArchUnit 规则）。

## 决策

在 `platform.audit` 定义端口：

```java
public interface AccessDeniedAuditPort {
    void recordDenied(String actor, String obj, String act, String path, String method, String clientNet, String traceId);
}
```

- `security`：依赖 platform，注入该端口（`ObjectProvider`，可缺省），在写出 403 之前调用。
- `audit`：提供实现 `AccessDeniedAuditRecorder`，内部调用 `AuditWriter`（`REQUIRES_NEW`）。
- 端口实现缺失（例如单模块测试）→ 只记日志计数，不影响 403 返回。

## 理由

1. `security → platform` 与 `audit → platform` 都是允许的依赖方向，端口是实现解耦的最小手段（不是「接口 + 实现 + 工厂」三层，只有一个实现）。
2. 放在 `JsonAccessDeniedHandler` 里是唯一可靠位置：`ExceptionTranslationFilter` 在过滤器链就处理了 `AccessDeniedException`，`@RestControllerAdvice` 不会触发。
3. 同步调用（不异步）才能满足「403 返回后立刻能查到 DENIED」的验收。

## 后果

- 正：无跨模块 import；审计失败不影响 403；测试可用 fake 端口。
- 负：platform 多了一个 audit 语义的端口（不算业务依赖，仍属横切契约）。
- 约束：`recordDenied` 内部必须吞掉所有异常（审计写失败只计数）。

## 替代方案

- 事件总线异步：延迟不可控，验收可能 flaky。
- `security` 直接 import `audit`：违反模块规则。
- 在 `@RestControllerAdvice` 里兜：根本不触发。
