# 实施计划 v1（implementation-plan）

> 状态：v1.0 草案。本文件是 TriageDeck 从设计文档到可交付产品的阶段性计划，与 `docs/ai-tasks.md` 对应，每个 Milestone 结束后应有可验证的增量。微服务拆分**不在本期计划内**，仅作为后续决策点保留。

---

## 1. 总体策略

| 决策 | 内容 |
|------|------|
| 架构 | 模块化单体先行（`platform/security/alert/audit/ai/notify/app`），跨模块仅通过端口、领域事件、注解通信，预埋微服务拆分边界 |
| 部署 | `docker compose up -d --build` 一条命令启全部（满足 NFR-01） |
| CICD | GitHub Actions：push → 编译测试 → 构建镜像 → 推送 GHCR → 部署 |
| 微服务 | 本期不拆分。待单体 CICD 跑通、业务稳定后，若出现独立发布频率高或需独立扩容的痛点，再按 `notify → ai → audit` 顺序选择性拆分 |

---

## 2. 里程碑规划

### M0 环境准备（第 1–2 天）

**目标**：让 `./gradlew`、`docker compose` 在本机可用，仓库可提交。

| 任务 | 内容 | 交付物 | 验收（DoD） | 完成日期 / 环境 |
|------|------|--------|-------------|----------------|
| T-ENV-01 | 安装 Gradle 并生成 wrapper | `gradlew`、`gradle/wrapper/` | `./gradlew --version` 输出版本 | 2026-11-24 / codespace |
| T-ENV-02 | 启动 Docker Desktop，安装 compose v2 插件 | Docker 可用 | `docker compose version` 正常输出 | 2026-11-24 / codespace |
| T-ENV-03 | 初始化 Git 仓库并关联远程 | `.git/`、远程 `origin` | `git remote -v` 可见 origin | 2026-11-24 / codespace |

---

### M1 单体骨架与基础设施（第 3–5 天，对应 T01）

**目标**：项目能编译、Flyway 建表、Docker 容器启动、健康检查通过，CICD 首次触发。

| 任务 | 内容 | 交付物 | 验收（DoD） | 完成日期 / 环境 |
|------|------|--------|-------------|----------------|
| T01 | Gradle 多模骨架 + `V1__init.sql` + `Dockerfile` + `docker-compose.yml` | `settings.gradle`、各模块 `build.gradle`、`gradle/libs.versions.toml`、`app/src/main/resources/db/migration/V1__init.sql`、`Dockerfile`、`docker-compose.yml` | `./gradlew build` 通过；`docker compose up -d` 四容器 healthy；`curl localhost:8080/actuator/health` 返回 `{"status":"UP"}` | - |
| T-CICD-01 | GitHub Actions CI（编译 + 测试） | `.github/workflows/ci.yml` | push/main 触发，`gradle test` 通过 | - |
| T-CICD-02 | GitHub Actions CD（镜像构建 + 推送 + 部署） | `.github/workflows/cd.yml` | 镜像推送 GHCR，`docker compose` 拉取运行 | - |

---

### M2 核心业务模块实现（第 6–12 天，对应 T02–T10）

**目标**：认证授权、告警摄入/列表/清除、审计写入/查询/导出、Kafka 两链路全部可用。

| 任务 | 模块 | 核心内容 | 验收 | 完成日期 / 环境 |
|------|------|----------|------|----------------|
| T02 | security | Keycloak OIDC+PKCE，JWT 校验，`/api/whoami` | 无 token 401，登录后返回 roles/permissions | - |
| T03 | security | Casbin 策略加载，`@RequirePermission` 拦截，`/api/_perm/check` | 无权访问 403，策略改重启即生效 | - |
| T04 | alert | HTTP/Kafka 告警摄入，fingerprint 去重（upsert），`processed_event` 幂等 | 同指纹 10 分钟内只有一行，`occurrence_count` 累加 | - |
| T05 | alert | 活动列表、详情，分页排序，筛选 | 返回 `AlertView` 数组，非 null | - |
| T06 | alert | 清除告警（`alerts_active → alerts_history` 原子移动） | 单事务两语句，并发清除一成一败，历史 1 行 | - |
| T07 | alert | 历史告警查询 | `cleared_at` 倒序，支持按 `clearedBy`/`source` 筛选 | - |
| T08 | audit | 审计写入（`@Auditable` 切面、403 审计、Keycloak 采集） | 登录/登出/越权均有审计记录，401 不审计 | - |
| T09 | audit | 审计列表、导出 CSV、脱敏 | 10 万行查询 <1s，CSV 表头中文，IP 脱敏 | - |
| T10 | platform/notify | Kafka 两链路，DLT，事务后发布，通知落库 | 回滚不发消息，DLT 后人工可排查 | - |

> 每个 T 任务开始前，按 AGENTS.md 写“实现计划（不超过 10 行）”，再写代码。

---

### M3 AI 能力与前端（第 13–16 天，对应 T11–T13）

**目标**：AI 简报、自然语言查询可用，前端 React 首屏可交互，类型由 openapi 生成。

| 任务 | 内容 | 验收 | 完成日期 / 环境 |
|------|------|------|----------------|
| T11 | AI 简报 SSE 流式，只读端口，mock 降级 | 首字节 <2s，模型不可达返回 503 | - |
| T12 | AI 自然语言查询，白名单过滤，不生成 SQL | 返回 `filter` JSON，`droppedFields` 透明 | - |
| T13 | 前端 React+Vite+TanStack Query，OIDC 内存 token，类型生成与对齐 | 首屏 2s，`npm run typecheck` 通过 | - |

---

### M4 验收与演示（第 17 天）

**目标**：满足所有 NFR，可完整 8 分钟演示剧本。

| 任务 | 内容 | 验收 | 完成日期 / 环境 |
|------|------|------|----------------|
| T-NFR | NFR-01 一条命令启全部；NFR-07 8 分钟剧本；NFR-08 离线演示（AI mock） | `docker compose up -d --build` 后 8 分钟内走完主剧本；断网主流程仍可演示 | - |

---

## 3. CICD 详细设计

### 3.1 触发策略

| 事件 | Workflow | 动作 |
|------|----------|------|
| push 到 `main` / `develop` | `ci.yml` | 编译 + 单元测试 + 前端 typecheck + 构建 |
| PR 到 `main` | `ci.yml` | 同上，不构建镜像 |
| push 到 `main` | `cd.yml` | 构建 Docker 镜像 → 推送 GHCR → 部署 |

### 3.2 CI（`.github/workflows/ci.yml`）关键步骤

1. Checkout 代码
2. Setup Java 21 (temurin)
3. 启动 service containers: `postgres:16`、`kafka:3.8`
4. `./gradlew test`
5. 前端：`npm ci` → `npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts` → `npm run typecheck` → `npm run build`

### 3.3 CD（`.github/workflows/cd.yml`）关键步骤

1. Checkout 代码
2. Setup Java 21
3. `./gradlew bootJar`
4. 前端 build，产物拷贝到 `app/src/main/resources/static/`
5. Docker login GHCR（`secrets.GITHUB_TOKEN`）
6. `docker/build-push-action` 推送 `ghcr.io/kuiwa/triagedeck:latest`
7. 部署 job（self-hosted runner 或手动）：`docker compose pull app && docker compose up -d app`

> 若暂无远程部署机器，第 7 步可先注释，保留“推代码自动构建并推送镜像”即可实现核心价值。

### 3.4 环境变量与密钥

- `KEYCLOAK_ISSUER_URI`
- `KC_COLLECTOR_SECRET`
- `GHCR_TOKEN`（GitHub Secrets）

---

## 4. 阶段性检查点与演示标准

| 检查点 | 对应 Milestone | 可看结果 | 验收命令示例 |
|--------|--------------|----------|-------------|
| CP1 | M0 | 环境可用 | `./gradlew --version`、`docker compose version` |
| CP2 | M1 | 单体可启动 | `docker compose up -d`、`curl /actuator/health` |
| CP3 | M2 | 业务端点可用 | `POST /api/alerts/ingest`、`GET /api/alerts/active`、`GET /api/audit/events` |
| CP4 | M3 | AI 与前端可用 | `POST /api/ai/briefing/{id}` (SSE)，前端首屏 |
| CP5 | M4 | 全流程演示 | `docker compose up -d --build`，8 分钟剧本 |

---

## 5. 微服务拆分决策点（后续）

当出现以下任一情况，可触发微服务拆分评估：

1. 某个模块（如 `ai`、`notify`）需要独立扩容或独立发布频率极高；
2. 团队拆分，不同小组负责不同模块；
3. 单体镜像构建时间超过可接受阈值。

**拆分顺序建议**：`notify` → `ai` → `audit` → `alert/security`（难度递增）。

**拆分前必须解决**：
- `alerts_active` ↔ `alerts_history` 原子移动（分布式事务或 Saga）
- 共享数据库拆分策略（是否数据库 per service）
- Casbin 授权跨服务同步
- Kafka 消费幂等与业务写入原子性

---

## 6. 文档更新记录

| 版本 | 日期 | 变更 | 作者 |
|------|------|------|------|
| v1.0 | 2026-11-24 | 首版：总体策略、M0–M4 里程碑、CICD 设计、微服务决策点 | AI 生成 |

---

## 7. 与现有文档对应关系

| 计划章节 | 关联文档 |
|----------|----------|
| 架构策略 | `docs/02-design.md` §1、§10 |
| 里程碑任务 | `docs/ai-tasks.md` T01–T13 |
| 数据库建表 | `docs/db/schema-v1.md`、`docs/02b-contract.md` §2 |
| 接口契约 | `docs/api/openapi.yaml`、`docs/02b-contract.md` §3 |
| 错误码/枚举 | `docs/02b-contract.md` §1、§4 |
| CICD 命令 | `docs/02-design.md` §9.3、AGENTS.md §6 |

使用本文档时，每完成一个 Milestone，应在 `docs/ai-log/Txx.md` 记录输入/返工/人工改动，并在此计划的对应章节标记完成。