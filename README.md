# TriageDeck

> 告警分诊台 + 统一审计的控制台。单进程 Spring Boot 模块化单体，前端静态资源打进同一个 jar；
> Docker Compose 一键拉起 app / Keycloak / PostgreSQL / Kafka 四容器。

**分支状态**：`develop2` 已完成 v1 主体功能（T01–T12），T13 收尾中，待合并。合并前请走一遍 [合并前检查清单](#12-合并前检查清单)。

---

## 1. 功能一览

| 能力 | 说明 | 前端入口 |
|---|---|---|
| 告警工作台 | 活动告警分页、严重度看板、按级别/来源/时间/关键词筛选 | 「活动告警」 |
| 告警详情 | 最新载荷、合并字段、事件时间线、清除状态 | 点击列表行 |
| 清除告警 | 幂等清除（重复清除返回已清除），写 `ALERT_CLEARED` 审计 | 列表 / 详情 |
| 历史告警 | 已清除告警分页与筛选 | 「历史告警」 |
| 告警上报 | 页面表单 + `POST /api/alerts/ingest` + Kafka 两条链路 | 「告警上报」 |
| 统一审计 | 时间线、按动作/结果/actor/时间筛选、CSV 导出 | 「审计时间线」 |
| 登录/登出审计 | 每 15s 采集 Keycloak Realm Events（LOGIN/LOGOUT/LOGIN_ERROR） | 「审计时间线」 |
| AI 自然语言查询 | 把问题映射为筛选条件并返回命中告警 | 「AI 查询」 |
| AI 告警简报 | SSE 流式生成分诊简报（真实 LLM，失败回退关键词规则） | 告警详情 |
| 认证与授权 | Keycloak OIDC + PKCE；Casbin 授权；`/api/whoami` 暴露权限 | 顶部账户区 |
| 静态演示模式 | `VITE_MOCK_MODE=true` 无需后端，适合纯静态托管 | — |

## 2. 架构

### 2.1 模块依赖（Gradle 模块）

```text
app → security, alert, audit, ai, platform, notify
alert → platform, security
audit → platform, security
ai → alert(只读接口 AlertReadPort), platform, security
notify → platform
platform → 无业务依赖（注解、事件总线、Kafka、错误、ID、时间）
```

- 跨模块只允许：领域事件、`@Auditable` 注解、显式暴露的只读接口；不允许 import 别的模块 Repository。
- 模块边界目前靠包结构 + 评审约束（ArchUnit 依赖已预留，边界测试待补）。

### 2.2 请求链路

```text
Browser（SPA，Vite 开发或 jar 内静态资源）
  └─ Authorization: Bearer <access_token>
       └─ app: Spring Security(JWT 资源服务器) → Casbin 授权 → Controller → Service → Repository
                                                              └─ @Auditable 切面 → audit_event
```

### 2.3 消息链路

```text
                     ┌── alert.ingested ──→ 消费 → 指纹去重(insert ... on conflict) → alerts_active
外部设备 / 模拟器 ───┤
                     └── alert.cleared  ──→ 消费 → 状态迁移（活动 → 历史）
失败消息 → alert.ingested.dlt / alert.cleared.dlt
```

Kafka 单节点 KRaft。容器内 app 连 `kafka:9092`；宿主机工具连 `localhost:19092`（Homebrew Kafka 用 9092，两者并存）。

## 3. 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.x（虚拟线程）、Spring Data JPA、Flyway |
| 数据库 | PostgreSQL 16（JSONB、部分唯一索引） |
| 认证 | Keycloak 26（OIDC + PKCE），JWT 资源服务器 |
| 授权 | Casbin（jcasbin），策略在 `security/src/main/resources/casbin/policy.csv` |
| 消息 | Kafka（KRaft 单节点），spring-kafka |
| AI | OpenAI 兼容 Chat Completions（JDK HttpClient 直连）+ 关键词规则回退；Spring AI 依赖已预留未启用 |
| 前端 | React 19 + TypeScript + Vite + TanStack Query |
| 测试 | JUnit 5 + Spring Boot Test（当前以单测为主）；Testcontainers / ArchUnit 已在依赖清单预留，IT 基建待补 |

## 4. 目录结构

```text
.
├── app/                    Spring Boot 启动模块（配置、Flyway 迁移、静态资源）
├── platform/               横切：@Auditable、@RequirePermission、事件总线、ApiError、TraceIds
├── security/               Spring Security + Casbin 授权
├── alert/                  告警摄入/去重/列表/详情/清除/历史
├── audit/                  审计写入、Keycloak 事件采集、查询、导出
├── ai/                     自然语言查询、简报 SSE、LLM 网关
├── notify/                 通知
├── tools/device-simulator/ 告警模拟器（不参与应用运行）
├── frontend/               React SPA（独立 npm 工程）
├── keycloak/               realm-export.json（realm、客户端、演示账号）
├── scripts/                start-stack / start-frontend / Kafka 造数 / 建 topic
├── docs/                   需求、契约、设计、LLD、ADR、任务与追溯
├── Dockerfile              多阶段构建（Gradle → bootJar → JRE）
└── docker-compose.yml      app / keycloak / postgres / kafka(+kafka-init)
```

## 5. 快速开始

### 5.1 全栈容器（推荐）

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps                        # 四个容器应 healthy（kafka-init 为 exited 0）
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

打开：

| 地址 | 说明 |
|---|---|
| http://localhost:8080 | SPA（静态资源已打进 jar，由 app 提供） |
| http://localhost:8081 | Keycloak（master 管理员 `admin/admin`） |
| localhost:5432 | PostgreSQL（`triagedeck/triagedeck`） |
| localhost:19092 | Kafka 对外监听（容器内为 `kafka:9092`） |

首次启动时：Flyway 自动建表；`kafka-init` 显式创建 4 个 topic；Keycloak 导入 `keycloak/realm-export.json`。

> 也可以用交互式脚本：`./scripts/start-stack.sh`（可选只启动部分服务、是否重建 app）。

### 5.2 本地开发（依赖用容器，后端/前端跑宿主机）

```bash
./scripts/start-stack.sh            # 选择 postgres / kafka / keycloak
./scripts/create-kafka-topics.sh    # 宿主机 Kafka 场景显式建 4 个 topic
./gradlew :app:bootRun              # 后端 :8080
./scripts/start-frontend.sh         # Vite :5173（首次自动 npm ci）
```

- Vite 已配置 `/api` → `http://localhost:8080` 代理。
- 前端默认 Keycloak 地址 `http://localhost:8081`，可用 `VITE_KEYCLOAK_URL` 覆盖。
- 后端跑宿主机时 `KC_ADMIN_BASE_URL` 保持默认 `http://localhost:8081` 即可。

### 5.3 静态演示模式（无需后端）

```bash
cd frontend
VITE_MOCK_MODE=true npm run dev     # http://localhost:5173
VITE_MOCK_MODE=true npm run build   # 产物 frontend/dist，可部署到任意静态托管
```

模拟模式下跳过 Keycloak，用内置数据渲染列表与看板，清除按钮禁用。

## 6. 配置项

| 变量 | 默认值 | 作用 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/triagedeck` | 数据库地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `triagedeck` / `triagedeck` | 数据库账号 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka（容器内用 `kafka:9092`） |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8081/realms/triagedeck` | JWT issuer 校验 |
| `KEYCLOAK_JWK_SET_URI` | 同上 + `/protocol/openid-connect/certs` | JWKS（容器内用 `keycloak:8080`） |
| `KC_ADMIN_BASE_URL` | `http://localhost:8081` | 审计采集器访问 Keycloak Admin API（**容器内必须是 `http://keycloak:8080`**） |
| `KC_COLLECTOR_SECRET` | `triagedeck-audit-collector-secret`（compose 默认） | 采集器客户端密钥；留空则禁用登录/登出采集 |
| `AI_BASE_URL` | 空 | OpenAI 兼容网关地址，如 `https://api.openai.com/v1` |
| `AI_API_KEY` | 空 | 网关 API Key |
| `AI_MODEL` | `gpt-4o-mini` | 模型名 |
| `APP_AI_MOCK` | `false` | 置 `true` 走规则/模板，不调用外部 LLM |
| `VITE_KEYCLOAK_URL` | `http://localhost:8081` | 前端 OIDC authority |
| `VITE_MOCK_MODE` | 未设置 | `true` 启用静态演示模式 |

> 未配置 `AI_BASE_URL` / `AI_API_KEY` 时，AI 查询与简报自动回退到关键词规则，不会报错。

## 7. 演示账号与权限

| 账号 | 密码 | 角色 | 权限（Casbin） |
|---|---|---|---|
| `admin` | `admin` | admin | alert read/clear/ingest、audit read/export、ai briefing/query、whoami |
| `operator` | `operator` | operator | alert read/clear、audit read、ai briefing/query、whoami |
| `viewer` | `viewer` | viewer | alert read、audit read、ai briefing/query、whoami |

- `ingest-service`（confidential client，`service` 角色）用于 `alert:ingest`。
- `triagedeck-audit-collector`（confidential client + 服务账号）用于读取 Keycloak 事件，角色 `realm-management: view-events / view-realm`。
- 无权限操作会被拒绝，并写入 `ACCESS_DENIED` 审计。

## 8. API 一览

所有接口都需要 `Authorization: Bearer <access_token>`；错误统一返回 `{"code","message","traceId"}`；时间入参出参均为 ISO-8601 UTC。

| 方法 | 路径 | 权限对象 | 说明 |
|---|---|---|---|
| GET | `/api/whoami` | `whoami:read` | 当前用户、角色、Casbin 权限 |
| GET | `/api/alerts/active` | `alert:read` | 活动告警分页（severity/source/keyword/时间筛选） |
| GET | `/api/alerts/history` | `alert:read` | 历史告警分页 |
| GET | `/api/alerts/{id}` | `alert:read` | 告警详情（最新载荷、合并字段、事件、清除状态） |
| POST | `/api/alerts/{id}/clear` | `alert:clear` | 幂等清除 |
| POST | `/api/alerts/ingest` | `alert:ingest` | 上报告警（REST 入口） |
| GET | `/api/audit/events` | `audit:read` | 审计查询（from/to/actor/action/outcome/page/size） |
| GET | `/api/audit/events/export` | `audit:export` | CSV 导出（`format=csv`，超限 422） |
| POST | `/api/ai/query` | `ai:query` | 自然语言查询 |
| POST | `/api/ai/briefing/{id}` | `ai:briefing` | 告警简报，SSE（`event: delta` / `event: done`） |
| GET | `/api/_perm/check` | `_perm:check` | 权限自检；仅 `app.security.test-endpoints=true` 时注册 |

机器可读契约：`docs/api/openapi.yaml`；前端类型由 `openapi-typescript` 生成。

## 9. 审计采集（登录 / 登出）

审计有两个来源，都写入 `audit_event`：

1. **APP**：`@Auditable` 切面对 service 方法记录 `ALERT_INGESTED` / `ALERT_CLEARED` / `ACCESS_DENIED` 等；
2. **KEYCLOAK**：`KeycloakEventCollector` 每 15 秒拉取 Realm Events。

采集要点：

- token：`POST /realms/triagedeck/protocol/openid-connect/token`（`client_credentials`）；
- 事件：`GET /admin/realms/triagedeck/events?dateFrom=&first=&max=`（Bearer）；
- 映射：`LOGIN → LOGIN/SUCCESS`、`LOGOUT → LOGOUT/SUCCESS`、`LOGIN_ERROR → LOGIN_FAILED/FAILED`；
- **Keycloak 26 的事件表示没有 `id` 字段**，因此 `source_event_id` 由 `kc:{type}:{time}:{sessionId|userId|ip}` 生成；
- 写库统一走 `AuditWriter`，`audit_event.source_event_id` 上有部分唯一索引，重复采集不会重复入库；
- 游标表 `keycloak_cursor(id='realm-events')`；`dateFrom` 回退 1 天做重叠，`time < 游标` 的旧事件跳过；
- `LOGOUT` 事件不含 `username`，用同页 `LOGIN` 的 `userId → username` 映射解析（无需额外授 `view-users`）。

自查 SQL：

```bash
docker compose exec postgres psql -U triagedeck -c \
  "select action,outcome,actor,source,occurred_at from audit_event order by occurred_at desc limit 10;"
```

关键日志：

```bash
docker compose logs app | grep -E 'keycloak_collect|keycloak_insert_failed'
# 正常：action=keycloak_collect processed=1 lastEventTime=...
```

## 10. 常用命令

```bash
# 编译 + 单测
./gradlew build
./gradlew :alert:test
./gradlew :audit:test

# 前端
cd frontend && npm run typecheck && npm run build

# OpenAPI → TS 类型（生成物需提交）
cd frontend && npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts

# 容器
docker compose ps
docker compose logs -f app
docker compose down            # 不要加 -v，保留 PostgreSQL 数据

# Kafka 造数
./scripts/create-kafka-topics.sh
./scripts/send-kafka-alerts.sh                          # 交互式
./scripts/send-kafka-alerts.sh --count=10 --severity=MIXED --type=threshold -y
./scripts/send-kafka-alerts.sh --doctor                 # 只体检链路，不发消息

# 设备模拟器
./gradlew :device-simulator:run --args="--count=10 --mode=kafka"
./gradlew :device-simulator:run --args="--count=10 --mode=kafka --bootstrap=localhost:19092"
```

## 11. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 登录后接口 401 | token 过期或 issuer 不匹配；确认 `KEYCLOAK_ISSUER_URI` 与浏览器访问的 Keycloak 地址一致 |
| 采集器 `401 invalid_client` | realm 里没有 `triagedeck-audit-collector`，或 secret 不一致；重建 Keycloak 或在线补客户端与角色 |
| 采集器 `Connection refused` | `KC_ADMIN_BASE_URL` 错：**容器内是 `http://keycloak:8080`**，宿主机是 `http://localhost:8081` |
| 采集器 `events_disabled` | realm 未启用 events；确认导入的是最新 `keycloak/realm-export.json` |
| `audit_event` 没有登录记录 | 同上；另外 Keycloak 只在真实登录/登出动作时产生事件 |
| 改了 `realm-export.json` 不生效 | Keycloak 无数据卷，`--import-realm` 只在**创建容器**时导入：`docker compose up -d --force-recreate keycloak` |
| 前端 404 / 界面是旧的 | `app/src/main/resources/static` 仍是旧构建；按第 12 节重新构建并同步 |
| 前端请求 404 | 开发时用 `:5173`（带 `/api` 代理），不要直接用静态服务器打开 |
| 简报 SSE 失败 | 必须 POST + `Accept: text/event-stream`（前端已用 fetch 流解析，不要用 EventSource） |

## 12. 合并前检查清单

```bash
# 1) 后端编译 + 单测
./gradlew build

# 2) 前端类型检查 + 构建
cd frontend && npm run typecheck && npm run build

# 3) 把前端产物同步进 jar（static 是提交物，必须与源码一致）
rm -rf ../app/src/main/resources/static/assets
cp -R dist/* ../app/src/main/resources/static/

# 4) compose 语法
cd .. && docker compose config -q

# 5) 全栈重建 + 健康检查
docker compose up -d --build
curl -s localhost:8080/actuator/health
```

冒烟剧本（约 8 分钟）：

1. `admin/admin` 登录，`whoami` 显示角色与权限；
2. 活动告警有数据（没有就 `./scripts/send-kafka-alerts.sh --count=5 -y`）；
3. 点开详情：字段、事件、最新载荷正常；
4. 清除一条：活动列表减少、历史列表出现；
5. 审计时间线出现 `ALERT_CLEARED`；退出/重新登录后 ≤15s 出现 `LOGIN` / `LOGOUT`；
6. `viewer/viewer` 登录后清除按钮不可用；直接调清除接口返回 403 且审计出现 `ACCESS_DENIED`；
7. AI 查询「最近一小时的高危告警」有结果；详情页简报能流式输出（未配 LLM 时走规则回退）。

## 13. 已知限制

- `AlertReadPort` 只读 `alerts_active`：AI 自然语言查询与简报目前仅覆盖**活动告警**，历史范围不支持。
- AI 查询与告警上报视图的样式仍以基础布局为主（部分类名尚无样式定义）。
- Keycloak 无持久化卷：重建容器会重置 realm（演示账号随导出文件恢复，手工改动会丢失）。
- 前端静态资源是提交物：改前端后必须重新构建并同步到 `app/src/main/resources/static`（见第 12 节）。

## 14. 文档索引

| 你想知道 | 读这个 |
|---|---|
| 需求原文（US 编号） | `docs/01-requirements.md` |
| 本期范围与需求勘误 | `docs/03-scope-v1.md` |
| 总体设计与端点地图 | `docs/02-design.md` |
| 字段/枚举/错误码/表结构/接口（唯一真相） | `docs/02b-contract.md` |
| 各模块实现细节 | `docs/lld/01-auth.md` … `docs/lld/05-platform-notify.md` |
| 接口契约（机器可读） | `docs/api/openapi.yaml` |
| 数据库 ER / 索引 | `docs/db/schema-v1.md` |
| 任务与验收 | `docs/ai-tasks.md` |
| 决策记录 | `docs/adr/0001…0010` |
| 需求追溯 | `docs/traceability.md` |
| 每个任务的实施记录 | `docs/ai-log/Txx.md` |

总入口 `docs/00-index.md`；硬约束 `AGENTS.md`。

## 15. 二期

见 `docs/03-scope-v1.md §3`（自动清除、审计哈希链、交接摘要等）。



