# 实施计划 v1（implementation-plan）

> 状态：v1.1 草案。本文件是 TriageDeck 从设计文档到可交付产品的阶段性计划，与 `docs/ai-tasks.md` 对应，每个 Milestone 结束后应有可验证的增量。微服务拆分**不在本期计划内**，仅作为后续决策点保留。

---

## 1. 总体策略

| 决策 | 内容 |
|------|------|
| 架构 | 模块化单体先行（`platform/security/alert/audit/ai/notify/app`），跨模块仅通过端口、领域事件、注解通信，预埋微服务拆分边界 |
| 本地部署 | `docker compose up -d --build` 一条命令启全部（满足 NFR-01） |
| 预览部署 | GitHub Actions 将完整应用镜像推送到 GHCR，并通过当前 Codespace 上的 self-hosted runner 更新私有预览 |
| 预览边界 | 这是 Codespace 在线期间可访问的临时预览，不是常驻生产环境；Codespace 停止时页面和 runner 均不可用 |
| CICD | PR 在 GitHub-hosted runner 上运行 CI；main 合并后构建、发布完整镜像，再部署到在线 Codespace 并执行 smoke checks |
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
| T-CICD-02 | GitHub Actions CD（前后端镜像 + GHCR + Codespaces 私有预览） | `.github/workflows/cd.yml`、`Dockerfile`、部署用 Compose 配置 | main 合并后构建含前端静态资源的 app 镜像，推送 SHA 与 `latest` tag；Codespace runner 拉取该 SHA 镜像并更新服务；端口保持 private；首页、健康检查、OIDC 登录及 `/api/whoami` 验证通过 | 后端镜像 GHCR 推送已验证；前端打包、自动部署与端到端验收未完成 |

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
| push 到 `main` | `cd.yml` | 构建完整 Docker 镜像 → 推送 GHCR → 部署到 Codespace → smoke checks |

CD 只部署 `main` 上的受信任提交。PR、特别是来自 fork 的 PR，不得在 Codespace self-hosted runner 上执行。main 分支应要求 CI 检查通过后才能合并；部署 job 只在镜像构建和推送成功后运行。

### 3.2 CI（`.github/workflows/ci.yml`）关键步骤

1. Checkout 代码
2. Setup Java 21 (temurin)
3. 启动 service containers: `postgres:16`、`kafka:3.8`
4. `./gradlew test`
5. 前端：`npm ci` → `npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts` → `npm run typecheck` → `npm run build`

### 3.3 CD（`.github/workflows/cd.yml`）关键步骤

1. Checkout 代码
2. 构建 Spring Boot jar 和前端静态资源，并将前端资源包含在同一个 app 镜像中；镜像启动后由 app 提供前端页面和 API。
3. 使用 `GITHUB_TOKEN` 登录 GHCR，推送不可变的 commit SHA tag 和便于人工使用的 `latest` tag。
4. 仅在镜像发布成功后运行部署 job；job 指定 Codespace 的 self-hosted runner，使用该 commit SHA 镜像执行 Compose pull/up，并等待 app 健康检查通过。
5. 部署后检查首页、`/actuator/health` 和 Keycloak OIDC discovery；再通过浏览器完成 operator 登录并确认 `/api/whoami` 返回身份与权限。
6. 任一部署或健康检查失败都必须让 job 明确失败；保留上一个成功部署的镜像 tag，以便回滚，不得以 `latest` 作为部署回滚依据。

Codespace runner 只接受 main 部署工作流，不用于 CI 或 PR。首次配置 runner 时，在 GitHub 仓库设置中临时生成注册 token，并只用于 Codespace 本地注册；不得提交 token、PAT 或 runner 凭据到仓库、日志或聊天。若 GHCR package 为私有，部署所需的只读拉取凭据通过受限的 GitHub secret 提供，不写入镜像或 Compose 文件。

Codespaces 转发端口必须设为 **Private**。预览使用 Codespaces 提供的 HTTPS 转发地址；app 的公开 URL、Keycloak 的公开 issuer/回调地址及后端内部 JWK 地址须分别配置并保持 issuer 一致。Keycloak client 只允许当前预览的精确 HTTPS redirect URI，不保留宽泛通配符。

首次部署前需人工完成 Codespace runner 注册、端口可见性设为 Private、GitHub Actions secret/environment 配置及 Keycloak URL 配置。Codespace 停止或重建后，页面不可访问，runner 也可能离线；恢复后需确认 runner 已在线，必要时重新启动或注册 runner。此预览不提供 Codespace 停止期间的自动部署或可用性保证。

### 3.4 环境变量与密钥

- `KEYCLOAK_ISSUER_URI`
- `KC_COLLECTOR_SECRET`
- GHCR 发布使用 workflow 的 `GITHUB_TOKEN` 与最小 `packages: write` 权限。
- 若私有 GHCR package 需要在 Codespace 拉取，使用 GitHub secret 中的只读 package 凭据；不在仓库中保存明文凭据。
- Codespaces app/Keycloak HTTPS 转发 URL 与精确 OIDC redirect URI 作为部署配置维护；不得把临时 runner 注册 token 当作长期 secret。

---

## 4. 阶段性检查点与演示标准

| 检查点 | 对应 Milestone | 可看结果 | 验收命令示例 |
|--------|--------------|----------|-------------|
| CP1 | M0 | 环境可用 | `./gradlew --version`、`docker compose version` |
| CP2 | M1 | 单体可启动 | `docker compose up -d`、`curl /actuator/health` |
| CP3 | M2 | 业务端点可用 | `POST /api/alerts/ingest`、`GET /api/alerts/active`、`GET /api/audit/events` |
| CP4 | M3 | AI 与前端可用 | `POST /api/ai/briefing/{id}` (SSE)，前端首屏 |
| CP5 | M4 | 全流程演示 | `docker compose up -d --build`，8 分钟剧本 |
| CP6 | M1 / T-CICD-02 | Codespaces 私有预览 | main 合并后 runner 部署 SHA 镜像；首页和 health check 通过；浏览器 operator 登录并验证 `/api/whoami`；Codespaces 端口为 Private |

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
| v1.1 | 2026-11-25 | 明确 T-CICD-02 交付完整前后端镜像及 Codespaces 私有预览，补充 runner 安全边界、HTTPS/OIDC 配置、部署验收与 Codespace 停止限制 | AI 辅助 |

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