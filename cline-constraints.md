# Cline 约束与提示（TriageDeck）

本文件由 Cline LLM 阅读，用于在本仓库工作时保持与项目规范一致。

> **优先级说明**：本文件是对 `AGENTS.md` 的补充与 Cline 侧提示。若两者有冲突，以 `AGENTS.md` 为准；若 `AGENTS.md` 未涉及 Cline 特定行为，以本文件为指引。

## 1. 项目概况

- 单进程 Spring Boot 模块化单体，前端静态资源打进同一个 jar。
- 后端：Java 21，Spring Boot 3.5.x，虚拟线程开启。
- 前端：React 19 + TypeScript + Vite + TanStack Query + Tailwind。
- 认证：Keycloak（OIDC + PKCE）；授权：Casbin（不设网关）。
- 数据库：PostgreSQL 16，Flyway 迁移。
- 消息：Kafka 单节点 KRaft。
- AI：Spring AI OpenAI Starter，AI 模块只能读和生成文本，不可调用写服务。

## 2. 模块与依赖边界（严格遵守）

```
app → security, alert, audit, ai, platform, notify
alert → platform, security
audit → platform, security
ai → alert(只读接口), platform, security
notify → platform
platform → 无业务依赖
```

- 跨模块只允许：领域事件、`@Auditable` 注解、显式暴露的只读接口。
- **不许跨模块 import 别人的 Repository**。
- `platform` 里放：`@Auditable`、`@RequirePermission`、`DomainEventBus`、`ApiError`、`TraceIds`、`Severities`。
- 每个模块都要有一条 ArchUnit 测试断言上述规则。

## 3. 后端代码规范

### 3.1 包名与分层
- 包名：`io.github.<handle>.triagedeck.<module>.<layer>`，layer ∈ `api/service/domain/repo/config`。
- DTO：`record`，后缀 `Request` / `View` / `Detail`，放在 `api` 包。
- 实体：JPA `@Entity`，放 `domain`，**不直接返回给前端**。
- ID：`UUID`，主键名 `id`。

### 3.2 枚举与时间
- 枚举严格按设计文档：`Severity{CRITICAL,HIGH,MEDIUM,LOW,INFO}`、`Outcome{SUCCESS,DENIED,FAILED}`、`ActorType{USER,SYSTEM,SERVICE}`。
- 时间入参出参用 ISO-8601 UTC 字符串，DB 用 `timestamptz`，Java 用 `Instant`。
- 集合返回一律空数组，不返回 null。

### 3.3 异常与事务
- 业务异常 `BizException(ApiErrorCode, HttpStatus)`，不抛裸 `RuntimeException`。
- 所有对外接口错误响应统一为 `{"code","message","traceId"}`。
- `@Transactional` 只标在 service 的 public 方法上，**不允许同类自调用**。
- 审计写入用 `REQUIRES_NEW`。

### 3.4 Kafka 与日志
- 用 Spring Boot 自动配置 + `application.yaml`，**不许手写 `ProducerFactory` / `KafkaTemplate` bean**。
- 日志键值对风格：`log.info("action={} alertId={} traceId={}", ...)`。
- **不许在日志里打印 token、请求体、完整 IP、密码**。

## 4. 前端代码规范

- 结构：`src/features/{alerts,audit,ai}/{components,hooks,api}.ts`；共享放 `src/shared`。
- 取数只用 TanStack Query；**禁止在 `useEffect` 里 fetch；禁止手写 axios 实例**。
- **不许前端直接请求 Keycloak Admin API**，也不许把 access token 放 localStorage / sessionStorage。
- **不许用 `any`（TypeScript）**，不许 `@ts-ignore`。
- 类型由 `openapi-typescript` 从 `docs/api/openapi.yaml` 生成（提交生成物），不手写接口类型。

## 5. 依赖与 DDL

- **不许新增依赖**。需要的库已在 `gradle/libs.versions.toml` 列全；确实缺，先提出，等人工确认。
- **不许改 DDL 字段名/类型**。表结构以 `docs/02b-contract.md §2` 为准；改字段必须先改契约、再补 ADR。
- **不许"先 select 再 insert"做去重**。必须用 `insert ... on conflict (fingerprint) do update`。
- **不许为了让测试通过而修改已完成任务的测试断言**。
- **不许"顺手重构"或"顺便加功能"**。只改当前任务白名单里的文件。

## 6. 测试规范

- 测试命名：`should_x_when_y()`；集成测试后缀 `IT`。
- 造数统一走 `testFixtures` 里的 `XxxFixtures`（如 `AlertFixtures.aCriticalAlert()`）。
- 集成测试继承 `ITBase`（含 Testcontainers 与已登录用户）。
- 断言审计/告警用共享的 `assertThat...` 辅助，不各写一遍。

## 7. 提交与记录（每个任务结束）

1. 一个任务一个 commit，格式：`Txx(module): 描述`
2. 提交信息末尾标注来源：`[ai-generated]` / `[ai-assisted]` / `[hand-written]`
3. 在 `docs/ai-log/T<编号>.md` 记三行：读了什么 / 几次返工 / 人工改了哪里
4. 在 `docs/traceability.md` 补上该任务覆盖的 US 编号

## 8. 必须停下来询问用户的情形

- 需要改 DDL、改接口契约、加依赖、跨模块调用
- 需求文档里有两条 US 冲突
- 验收命令跑不过但原因不明
- 需要引入清单里没有的库或中间件

宁可停下来问，不要"猜一个看起来对的做法"。

## 9. 常用命令

```bash
./gradlew build                                   # 全量编译 + 单测
./gradlew :alert:test                             # 单模块测试
./gradlew :alert:test --tests '*ClearIT*'         # 单类集成测试
docker compose up -d postgres kafka               # 只起依赖
docker compose up -d --build                      # 全栈
docker compose ps                                 # 看健康状态
curl -s localhost:8080/actuator/health            # 应返回 {"status":"UP"}
cd frontend && npm run dev                        # 前端（代理 /api → 8080）
cd frontend && npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts
npx playwright test                               # e2e
```

## 10. Cline 特别提示（Windows PowerShell）

- 本机运行 Windows PowerShell，工作目录为 `c:\Users\kui.wang6\repository\TriageDeck`。
- **不要发送带外层中括号的命令格式**，例如 `["powershell -Command "..."]`。中括号会被 PowerShell 解析为类型/索引语法，导致命令"未结束"而卡住或报错。
- **避免在 `powershell -Command` 内部嵌套双引号**，例如 `powershell -Command "Test-Path '...'"` 在多层工具调用时容易被转义残缺。
- 推荐写法：
  - 检查文件：`powershell -NoProfile -Command "Test-Path 'c:\path\to\file.md'"`（单引号包裹路径，外层仅一层双引号）
  - 或直接执行脚本：`pwsh -File .\script.ps1`
  - 或避免 `-Command`，改用 `cmd /c` 或工具原生能力
- 若需要检查文件是否存在，优先使用工具原生能力，避免反复发送 `Test-Path` 命令导致终端阻塞。
- 不要假设本机已安装 `psql`、`gradle wrapper` 等工具；如不确定，先询问用户或使用 Docker 容器内命令。