# 需求追溯矩阵（traceability）

> 每条 US 一行；`状态` 列只有 `本期 / 二期`，**不允许空列**。
> 测试列写「计划」表示已进入 LLD 测试表但尚未实现（T13 交付时全部改为实际测试类名）。
> 端点编号对应 `02b-contract.md §3`（#1…#12）。

| US | 一句话 | 状态 | 设计章节 | LLD 小节 | 端点 | 表 | 测试 / T |
|---|---|---|---|---|---|---|---|
| A1 | 统一登录 | 本期 | §7.1 | `lld/01 §4.1 §5 §8` | #11 | — | `WhoamiIT` / T02 |
| A2 | 登出 | 本期 | §7.2 | `lld/01 §7 §8` | —（Keycloak） | — | e2e 登出 + 审计 LOGOUT / T02 T09 T13 |
| A3 | 角色差异 | 本期 | §4.3 §4.4 | `lld/01 §2 §5` | #11 #12 | — | `WhoamiIT` `PermissionCheckIT` / T03 |
| A4 | 后端拦截越权 | 本期 | §4 §6.1 | `lld/01 §4.2 §7` | #4 #12 | `audit_event` | `AccessDeniedAuditIT` / T03 T08 |
| A5 | 授权可配置 | 本期 | §4.1 §4.4 | `lld/01 §3 §4.3` | #12 | — | `PermissionServiceTest` `PermissionCheckIT` / T03 |
| B1 | 双入口上报 | 本期 | §2.5 §5.1 | `lld/02 §4.1`、`lld/05 §4.1` | #5 | `alerts_active` `alert_event` | `AlertKafkaConsumerServiceTest` `NotificationConsumerServiceTest` `DomainEventBusTest` / T04 T10 |
| B2 | 去重聚合 | 本期 | §2.5 | `lld/02 §3.1 §4.1 §4.2` | #5 #3 | `alerts_active` `alert_event` | `AlertIngestIT` `FingerprintTest` / T04 |
| B3 | 活动列表 | 本期 | §3(#1) §2.1 | `lld/02 §3.3 §5` | #1 | `alerts_active` | `ActiveListIT` / T05 |
| B4 | 告警详情 | 本期 | §3(#3) §2.4 | `lld/02 §3.3 §5` | #3 | `alert_event` | `AlertDetailIT` / T05 |
| B5 | 清除移入历史 | 本期 | §2.6 §3(#4) | `lld/02 §3.2 §4.3` | #4 | `alerts_active` `alerts_history` | `ClearIT` / T06 |
| B6 | 历史查询 | 本期 | §3(#2) §2.2 | `lld/02 §3.3 §5` | #2 | `alerts_history` | `HistoryIT` / T07 |
| B7 | 自动清除（可选） | 二期 | §2.6（`clear_mode`） | — | — | `alerts_history.clear_mode` | 二期（ADR-0009） |
| B8 | 级别分布看板 | 本期 | §3(#1) §2.1 | `lld/02 §3.3` | #1 | `alerts_active` | `SeverityDashboard`（前端，复用 #1） / 增量 |
| C1 | 登录登出记录 | 本期 | §6.4 §7.3 | `lld/03 §4.3 §5` | #6 | `audit_event` `keycloak_cursor` | `KeycloakCollectIT` / T09 |
| C2 | 清除留痕 | 本期 | §6.1 §6.2 §6.3 | `lld/03 §4.1 §7` | #4 #6 | `audit_event` | `AuditWriteIT` / T06 T08 |
| C3 | 越权留痕 | 本期 | §6.1 | `lld/03 §4.2`、`lld/01 §4.2` | #4 #6 | `audit_event` | `AccessDeniedAuditIT` / T08 |
| C4 | 统一时间线 + 导出 | 本期 | §6.1 §3(#6 #7) | `lld/03 §3.2 §3.3 §5` | #6 #7 | `audit_event` | `AuditQueryIT` `AuditExportIT` / T08 T09 |
| C5 | 脱敏 | 本期 | §6（I-C2） | `lld/03 §7`、契约 §6.1 §6.2 | #6 #7 | `audit_event` | `AuditDetailSanitizerTest` `IpMaskerTest` / T08 |
| C6 | 完整性校验（可选） | 二期 | — | — | — | `audit_event`（预留 `prev_hash/hash`） | 二期（ADR-0009） |
| D1 | 告警简报 | 本期 | §8 §3(#8) | `lld/04 §4.1 §9` | #8 | `alert_event`（证据） | `BriefingIT` / T11 |
| D2 | 自然语言查询 | 本期 | §8 §3(#9) | `lld/04 §4.3 §9` | #9 | 经 `AlertReadPort` | `AiQueryIT` / T12 |
| D3 | 交接摘要（可选） | 二期 | §3(#10) 契约位 | — | #10（未实现） | `audit_event` | 二期（ADR-0009） |
| D4 | AI 只建议不动手 | 本期 | §8 | `lld/04 §1 §6 §7`、`lld/05`（无写路径） | #8 #9 | — | `PromptBuilderTest` `AiArchTest` `BriefingIT` / T11 T12 |

## 统计

- 本期 20 条 / 二期 3 条（B7、C6、D3）——与 `03-scope-v1.md §1` 一致。
- 非功能：NFR-01…08 的落点见 `02-design.md §9.1`，度量命令见各 LLD §9。

## 交付检查（T13）

1. 上表无空单元格（`状态` 必须有值；二期行在 `LLD 小节` 写 `—` 是允许的）。
2. 每条 `测试` 列改为**真实存在的测试类名**，并在 `docs/ai-log/Txx.md` 记录实测结论。
3. 端点列必须能在 `02b-contract.md §3` 找到同编号小节。
