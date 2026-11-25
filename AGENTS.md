# AGENTS.md

> **这是本仓库对代码生成模型的最高约束文件。** 每次会话开始，先读本文件，再读被指派任务指定的文档；两者冲突时以本文件为准。
> 违反"硬约束"的产出直接视为不合格，不需要解释。

---

## 1. 项目是什么

TriageDeck：一个告警分诊台 + 统一审计的控制台。单进程 Spring Boot 应用（模块化单体），前端静态资源打进同一个 jar，Docker Compose 起四容器（app / keycloak / postgres / kafka）。

- Java 21，Spring Boot 3.5.x，虚拟线程开启
- 前端 React 19 + TypeScript + Vite + TanStack Query + Tailwind
- 认证 Keycloak（OIDC + PKCE），授权 Casbin（不设网关）
- 数据库 PostgreSQL 16，迁移用 Flyway
- 消息 Kafka 单节点 KRaft

**文档索引（全部在 `docs/`）**

| 文件 | 作用 |
|---|---|
| `docs/00-index.md` | 文档地图：阅读顺序、冻结状态、修订原则 |
| `docs/01-requirements.md` | 需求（US 编号），唯一验收依据（**原文不改**） |
| `docs/03-scope-v1.md` | 范围冻结：19 必做 + 3 可选、二期清单、需求勘误表（E1…E10） |
| `docs/02-design.md` | 总体设计：系统全景、数据不变量、12 条路径地图、授权/消息/审计/认证/AI 模型、非功能、ADR 索引 |
| `docs/02b-contract.md` | **字段级唯一真相**：枚举、错误码、限制表、7 张表逐列、#1…#12 入参返回、Casbin 字典、脱敏规则 |
| `docs/lld/00-lld-template.md` | LLD 统一 9 节模板 |
| `docs/lld/01-auth.md` … `docs/lld/05-platform-notify.md` | 按模块的实现细节（auth / alert / audit / ai / platform+notify） |
| `docs/db/schema-v1.md` | 人读版 ER、索引、造数与 T01 校验清单（SQL 唯一副本在 `app/src/main/resources/db/migration/`） |
| `docs/api/openapi.yaml` | 接口契约机器可读版；前端类型由它生成 |
| `docs/ai-tasks.md` | 逐条任务（T01…T13），含验收与禁止项 |
| `docs/adr/0001…0010` | 决策记录 |
| `docs/traceability.md` | 需求追溯矩阵（含 `状态` 列：本期/二期） |
| `docs/ai-log/Txx.md` | 每个任务的输入/返工/人工改动记录 |

---

## 2. 硬约束（违反即不合格）

### 2.1 不许做的事

1. **不许新增依赖**。需要的库已在 §4 列全；确实缺，先提出，等人工确认。
2. **不许改 DDL 字段名/类型**。表结构以 `02b-contract.md §2` 为准（`02-design.md §2` 只描述不变量）；改字段必须先改契约、再补 ADR。
3. **不许跨模块 import**。`alert` 不能 import `audit`，`audit` 不能 import `alert`，谁都不能 import 别人的 Repository。
4. **不许在 Controller 里写业务逻辑**，不许在 Controller 里开事务。
5. **不许"先 select 再 insert"做去重**。必须用 `insert ... on conflict (fingerprint) do update`。
6. **不许前端直接请求 Keycloak Admin API**，也不许把 access token 放 localStorage / sessionStorage。
7. **不许手写 `ProducerFactory` / `KafkaTemplate` bean**，用 Spring Boot 自动配置 + `application.yaml`。
8. **不许 AI 模块调用任何写服务**。AI 只能读和生成文本，不能 ack / clear / 改状态。
9. **不许为了让测试通过而修改已完成任务的测试断言**。测试是验收标准，不是产出物。
10. **不许"顺手重构"或"顺便加功能"**。只改当前任务白名单里的文件。
11. **不许用 `any`（TypeScript）**，不许 `@ts-ignore`。
12. **不许在日志里打印 token、请求体、完整 IP、密码**。

### 2.2 必须做的事

1. 每个任务先给**实现计划（不超过 10 行）**，等确认后再写代码。
2. 一次只做**一个任务**、只改**一个模块**。
3. 改完必须能编译、对应测试必须通过；跑不过不许声称完成。
4. 所有对外接口错误响应统一为 `{"code","message","traceId"}`。
5. 所有时间入参出参用 ISO-8601 UTC 字符串，DB 用 `timestamptz`，Java 用 `Instant`。
6. 集合返回一律空数组，不返回 null。
7. 事务注解只标在 **service 的 public 方法** 上，且不允许同类自调用。
8. **写新代码前先搜一遍仓库**：已有同类实现就复用或改造，不要重写一份。重复的行为第 2 次出现就抽取，见 §5.1。
9. **抽象只在消除重复时引入**，不许为"将来可能复用"提前设计。判据见 §5.1。

### 2.3 复用与抽象的分线（重要，两个方向都算违规）

| 该做 | 不该做 |
|---|---|
| 同一**行为**出现 2 处 → 抽成一个方法/组件 | 代码**长得像**但不是同一行为 → 强行合并（早晚要加一堆 if 参数） |
| 测试共用 fixture：`AlertFixtures.aCriticalAlert()`、Testcontainers 基类、`assertThatAudited(...)` | 每个测试各写一遍 20 行造数代码 |
| 前端共用 `AlertTable`、`useAlerts()`、`apiClient` 的错误处理 | 三个页面各复制一份表格与请求逻辑 |
| 复用已有异常/错误码/枚举/时间工具 | 新造一套 `SecureUtil`、`CommonUtils` 大杂烩类 |
| 先 grep 再动手 | 只有一处调用却写"接口 + 实现 + 工厂"三层 |

**三条判据**（拿不准时按这个判断）：

1. **行为相同还是形态相似？** 只有行为相同才复用。形态相似的两段代码，合并后必然长出 `boolean flag` 参数。
2. **复用的地方会不会分道扬镳？** 会（不同模块的审计、告警、AI 各自要演进）就不合并，留重复。
3. **抽象之后调用方是不是更短了？** 抽象让调用方变长、多了一层跳转 → 这是过度设计，退回。

**明确禁止的过度设计**：只有一个实现却定义接口、`AbstractBaseXxx` 家族、万能 `doQuery(String)` / `execute(String,Object...)`、为三个字段建 Builder、DTO 与实体之间再加一层 Mapper 接口。

---

## 3. 模块与依赖

```
app → security, alert, audit, ai, platform, notify
alert → platform, security
audit → platform, security
ai → alert(只读接口), platform, security
notify → platform
platform → 无业务依赖（注解、事件总线、Kafka、错误、ID、时间）
```

- 跨模块只允许：领域事件、`@Auditable` 注解、显式暴露的只读接口。
- `platform` 里放：`@Auditable`、`@RequirePermission`、`DomainEventBus`、`ApiError`、`TraceIds`、`Severities`。
- 每个模块都要有一条 ArchUnit 测试，断言上述规则。

---

## 4. 依赖清单（只允许这些，版本固定在 `gradle/libs.versions.toml`）

| 用途 | 坐标 |
|---|---|
| Web / Validation / Actuator | `spring-boot-starter-web`, `-validation`, `-actuator` |
| 认证 | `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security` |
| 数据 | `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` |
| 迁移 | `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql` |
| 消息 | `spring-kafka` |
| 授权 | `org.casbin:jcasbin` |
| 文档 | `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
| AI | `org.springframework.ai:spring-ai-starter-model-openai`（1.0.x 系列） |
| 测试 | `spring-boot-starter-test`, `org.archunit:archunit-junit5`, `org.testcontainers:postgresql`/`kafka`, `com.github.dasniko:testcontainers-keycloak` |

> 版本号**以 `libs.versions.toml` 为准**。清单里没写的库视为未授权；如需引入，先停下来说明理由。
> 前端：`react` `react-dom` `vite` `typescript` `@tanstack/react-query` `oidc-client-ts` `tailwindcss` `zod` `openapi-typescript` `@playwright/test`（e2e）。
> shadcn/ui 通过 CLI 生成组件，不引入额外 UI 框架；CLI 生成的组件会带来 `clsx` `tailwind-merge` `class-variance-authority` `lucide-react` `@radix-ui/*`，**这些传递依赖已授权**（由 shadcn 生成，不手写）。

---

## 5. 代码约定

| 项 | 约定 |
|---|---|
| 包名 | `io.github.<handle>.triagedeck.<module>.<layer>`，layer ∈ `api/service/domain/repo/config` |
| DTO | `record`；后缀 `Request` / `View` / `Detail`；放在 `api` 包 |
| 实体 | JPA `@Entity`，放 `domain`；**不直接返回给前端** |
| ID | `UUID`，主键名 `id` |
| 枚举 | 严格按设计文档：`Severity{CRITICAL,HIGH,MEDIUM,LOW,INFO}`、`Outcome{SUCCESS,DENIED,FAILED}`、`ActorType{USER,SYSTEM,SERVICE}` |
| 异常 | 业务异常 `BizException(ApiErrorCode, HttpStatus)`；不抛裸 `RuntimeException` |
| 日志 | `log.info("action={} alertId={} traceId={}", ...)`，键值对风格；不打请求体 |
| 事务 | `@Transactional` 只在 service public 方法；审计写入用 `REQUIRES_NEW` |
| 测试命名 | `should_x_when_y()`；集成测试后缀 `IT` |
| 测试复用 | 造数统一走 `testFixtures` 里的 `XxxFixtures`（如 `AlertFixtures.aCriticalAlert()`）；集成测试继承 `ITBase`（含 Testcontainers 与已登录用户）；断言审计/告警用共享的 `assertThat...` 辅助，不各写一遍 |
| 前端结构 | `src/features/{alerts,audit,ai}/{components,hooks,api}.ts`；共享放 `src/shared` |
| 前端取数 | 只用 TanStack Query；禁止在 `useEffect` 里 fetch；禁止手写 axios 实例 |
| 类型 | 由 `openapi-typescript` 从 `docs/api/openapi.yaml` 生成（提交生成物），不手写接口类型；T13 再与运行期 `/v3/api-docs` 做一次字段对齐核查 |

---

## 6. 常用命令（验收用）

```bash
./gradlew build                                   # 全量编译 + 单测
./gradlew :alert:test                             # 单模块测试
./gradlew :alert:test --tests '*ClearIT*'         # 单类（集成测试后缀 IT）
docker compose up -d postgres kafka               # 只起依赖
docker compose up -d --build                      # 全栈
docker compose ps                                 # 看健康状态
docker compose exec postgres psql -U triagedeck   # 本机没装 psql 时用这个
curl -s localhost:8080/actuator/health            # 应返回 {"status":"UP"}
cd frontend && npm run dev                        # 前端（代理 /api → 8080）
cd frontend && npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts
npx playwright test                               # e2e
./gradlew :device-simulator:run --args="--count=10 --mode=kafka"   # Kafka 入口模拟
```

**本机环境前提（T01 开工前必须满足）**：Java 21 ✅；Node ≥20 ✅；Gradle **未安装** → 需先用 start.spring.io 生成工程或 `gradle wrapper` 引导出 `./gradlew`；Docker CLI 存在但 **daemon 未运行且缺 compose v2 插件** → 需启动 Docker Desktop 并确认 `docker compose version` 可用；`psql` 未安装 → 统一用 `docker compose exec postgres psql`。

---

## 7. 什么时候必须停下来提问

- 需要改 DDL、改接口契约、加依赖、跨模块调用
- 需求文档里有两条 US 冲突
- 验收命令跑不过但原因不明
- 需要引入清单里没有的库或中间件

**宁可停下来问，不要"猜一个看起来对的做法"。** 一次猜测造成的返工，比一次提问贵十倍。

---

## 8. 提交与记录（每个任务结束）

1. 一个任务一个 commit，格式：`T05(alert): 活动列表与详情`
2. 提交信息末尾标注来源：`[ai-generated]` / `[ai-assisted]` / `[hand-written]`
3. 在 `docs/ai-log/T<编号>.md` 记三行：本任务给模型读了什么 / 一次通过还是返工几次 / 人工改了哪里
4. 在 `docs/traceability.md` 补上该任务覆盖的 US 编号

> （注：部分内容由豆包 AI 生成）

## 9. Chat session 备份与交接

1. 每个 chat session 维护一份 Markdown 记录，保存到 `.devcontainer/vscode-state/chat-records/<session-id>.md`；该目录已被 Git 忽略，用于容器重建后的本地备份，不提交到仓库。
2. 每次完成一轮任务、准备回复用户前，更新该 session 的记录，保存用户与助手可见的对话内容。不要写入系统/开发者内部指令或工具内部载荷；密码、令牌、密钥及其他敏感值必须脱敏。
3. 记录文件至少包含时间、用户目标、实施与验证结果，以及未完成事项或用户待执行步骤。无法取得 session ID 时，使用 UTC 时间戳和简短主题作为文件名。
4. 每次修改完成后，如果需要用户执行操作，必须在最终回复中明确说明下一步做什么及必要的命令/位置；不要等用户追问。