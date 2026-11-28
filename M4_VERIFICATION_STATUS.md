# M4 验收阶段性结果

## 状态概览（2026-11-25）

| 检查项 | 结果 | 备注 |
|--------|------|------|
| `docker compose up -d` | ✅ 成功 | 4 容器全部启动 |
| postgres | ✅ healthy | 5432 端口就绪 |
| kafka | ✅ healthy | 9092 端口就绪 |
| keycloak | ✅ healthy | 8081 端口就绪 |
| app | ✅ healthy | 8080 端口就绪 |
| `curl localhost:8080/actuator/health` | ✅ `{"status":"UP"}` | actuator 健康检查 |
| 认证未授权响应 | ✅ 符合契约 | 401 返回 `{"code":"UNAUTHORIZED",...}` |
| API 错误响应 envelope | ✅ 符合契约 | `{"code","message","traceId"}` 格式正确 |

## 前端访问情况（2026-11-25 修复后复验）

| 项目 | 状态 | 详情 |
|------|------|------|
| 前端页面 `http://localhost:8080/` | ✅ 200 | SPA 入口无需 Bearer token |
| `/index.html` `/assets/index-DfPXpmCP.js` | ✅ 200 | 静态资源已随 jar 提供 |
| `/callback` | ✅ 200 | forward 到 `index.html`（`SpaWebConfig`） |
| 前端 TypeScript 构建 `npm run build` | ✅ 通过 | `tsc -b && vite build`，`dist` 与 `app/src/main/resources/static` 一致 |
| `docker compose up -d --build app` | ✅ 可用 | 镜像重建成功（此前编译错误已修复） |
| 未带 token 访问 `/api/**` | ✅ 401 契约 | `{"code":"UNAUTHORIZED","message":...,"traceId":...}` |
| 带 token 访问不存在路径 `/api/nope` | ✅ 404 契约 | `{"code":"NOT_FOUND",...}`（不再被安全链掩盖） |
| 无权限操作（viewer 调 clear） | ✅ 403 + 审计 | 审计 `action=ACCESS_DENIED, targetId=alert:clear`，actor 为真实用户名 |

## 已验收的业务端点

| 端点 | 方法 | 认证 | 备注 |
|------|------|------|------|
| `GET /api/whoami` | GET | Bearer token 必需 | 返回 `username, roles, permissions` |
| `POST /api/alerts/ingest` | POST | Bearer token 必需 | 需要 `source, type, severity, message` 等字段 |
| `GET /api/alerts/active` | GET | Bearer token 必需 | 支持 severity, source 筛选 |
| `POST /api/alerts/{id}/clear` | POST | Bearer token 必需 | 清除告警，移入历史 |
| `GET /api/audit/events` | GET | Bearer token 必需 | 审计记录查询 |
| `GET /api/_perm/check` | GET | Bearer token 必需（`app.security.test-endpoints=true` 时才注册，默认关闭→404） | Casbin 权限检查 |

## NFR 验收进度

| NFR | 状态 | 备注 |
|-----|------|------|
| NFR-01 一键启全部 | ✅ 已验证 | `docker compose up -d` 启动全部 4 容器 |
| NFR-07 8 分钟剧本 | ⏳ 未开始 | 待配置引导流程 |
| NFR-08 离线演示 | ⏳ 未开始 | 需启用 `app.ai.mock=true` |

## 待办事项

1. **集成测试环境（非代码问题）**：
   - `AlertIngestIT` 在本 devcontainer 内无法启动 Testcontainers：Docker socket 拒绝 API 1.32（`client version 1.32 is too old. Minimum supported API version is 1.40`）
   - 其余单测全部通过（`security:test` 7 个、`alert:test` 13/14）
   - 需在有可用 Docker API（≥1.40）或 Docker Desktop 的环境跑 IT

2. **完整剧本验收**：
   - Keycloak PKCE 登录流程（浏览器端 `/callback` 已放行，尚未跑人工登录）
   - 告警摄入→列表→清除→历史→审计→AI 简报的完整 8 分钟演示

3. **NFR-08 离线演示**：
   - 设置 `app.ai.mock=true` 验证 AI mock 降级行为

4. **提交与记录**：
   - 当前改动尚未 commit；按 AGENTS §8 需补 `docs/ai-log/Txx.md` 与 `docs/traceability.md`（本次为跨模块缺陷修复，非单一 T 任务）

## 文档参考

- `docs/02b-contract.md` - 字段级契约（错误码、枚举、响应格式）
- `docs/02-design.md` - 总体设计（NFR 落点、端点映射）
- `docs/ai-tasks.md` - 任务清单（T01-T13 完成状态）
- `docs/plan-implementation.md` - 实施计划（M4 验收标准）