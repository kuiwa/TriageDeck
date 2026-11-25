# 实施计划 v1（implementation-plan）

> 状态：v1.3 草案。本文件是 TriageDeck 从设计文档到可交付产品的阶段性计划，与 `docs/ai-tasks.md` 对应，每个 Milestone 结束后应有可验证的增量。微服务拆分**不在本期计划内**，仅作为后续决策点保留。

---

## 1. 总体策略

| 决策 | 内容 |
|------|------|
| 架构 | 模块化单体先行（`platform/security/alert/audit/ai/notify/app`），跨模块仅通过端口、领域事件、注解通信，预埋微服务拆分边界 |
| 本地部署 | `docker compose up -d --build` 一条命令启全部（满足 NFR-01） |
| 镜像发布 | GitHub Actions 在 GitHub-hosted runner 构建前后端合一镜像，发布到 GHCR（commit SHA tag + `latest`） |
| 预览部署 | 用户按需启动 Codespace 并手动运行部署脚本；不配置常驻 self-hosted runner，不在 Codespace 上自动等待部署 |
| 成本边界 | Codespace 仅在需要查看预览时启动；停止可暂停计算用量，但 Codespace 存储仍按 GB-month 计量 |
| 镜像保留 | GHCR 保留最近 3 个不同的 SHA 镜像版本；Codespace 本地最多保留当前运行镜像和两个回滚版本；不自动删除数据库 volume |
| CICD | PR 与 main push 在 GitHub-hosted runner 运行 CI；main CI 成功后自动构建/发布镜像，预览由用户按需手动部署 |
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

| 任务 | 内容 | 交付物 | 验收（DoD） | 状态 | 完成日期 / 环境 |
|------|------|--------|-------------|------|----------------|
| T01 | Gradle 多模骨架 + `V1__init.sql` + `Dockerfile` + `docker-compose.yml` | `settings.gradle`、各模块 `build.gradle`、`gradle/libs.versions.toml`、`app/src/main/resources/db/migration/V1__init.sql`、`Dockerfile`、`docker-compose.yml` | `./gradlew build` 通过；`docker compose up -d` 四容器 healthy；`curl localhost:8080/actuator/health` 返回 `{"status":"UP"}` | ✅ 完成 | 2026-11-24 / Codespace |
| T-CICD-01 | GitHub Actions CI（编译 + 测试） | `.github/workflows/ci.yml` | PR 与 main push 的 `gradle test` 均通过 | ✅ 完成（用户确认 CI 失败已解决） | 2026-11-25 / GitHub Actions |
| T-CICD-02 | GHCR 前后端镜像发布 + Codespace 按需手动预览 | `.github/workflows/cd.yml`、`Dockerfile`、`scripts/deploy-codespaces-preview.sh`、Compose 配置 | main CI 成功后构建含前端静态资源的 app 镜像，发布 SHA 与 `latest` tag；清理时保留最近 3 个 SHA 版本；用户在 Codespace 运行脚本按需拉取指定 SHA、启动服务并通过健康/OIDC 检查；Ports 8080/8081 保持 private；脚本只清理未使用的旧 app 镜像，不删 volumes | ⏳ 方案已调整，待按此方案实施；不再采用常驻 Codespace runner | - |

**M1 当前状态与下一步（截至 2026-11-25）**

1. T01 骨架与基础设施验收完成。
2. T-CICD-01 已由用户确认 CI 失败已解决，按完成处理。
3. 下一项为 T-CICD-02：只让 GitHub Actions 自动构建/发布完整镜像；不让 Codespace 常驻 runner。用户启动 Codespace 后手动运行部署脚本并查看页面。
4. GHCR 的镜像存储/流量目前按 [GitHub Packages 计费说明](https://docs.github.com/en/billing/concepts/product-billing/github-packages)暂时免费；保留三版主要为版本管理与回滚，不应描述为当前 GHCR 费用优化。Codespace 停止期间不消耗计算时数，但仍可能计入[存储月用量](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-codespaces/about-billing-for-github-codespaces)。

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
| main push 对应的 CI 成功 | `cd.yml` | 构建完整 Docker 镜像 → 推送 GHCR SHA/`latest` tags → 保留最近 3 个镜像版本 |

CD 只发布 main 上 CI 成功的受信任提交；PR/fork 不发布镜像。CD 不连接 Codespace，也不部署运行服务。Codespace 部署是用户主动执行的一步，因此不需要维持在线 runner。

### 3.2 CI（`.github/workflows/ci.yml`）关键步骤

1. Checkout 代码
2. Setup Java 21 (temurin)
3. 启动 service containers: `postgres:16`、`kafka:3.8`
4. `./gradlew test`
5. 前端：`npm ci` → `npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/schema.d.ts` → `npm run typecheck` → `npm run build`

### 3.3 CD（`.github/workflows/cd.yml`）关键步骤

1. 在 main push 对应的 CI 成功后触发 CD；Checkout 必须使用该次 CI 的 commit SHA。
2. 构建 Spring Boot jar 和前端静态资源，并将前端资源包含在同一个 app 镜像中。
3. 使用 `GITHUB_TOKEN` 登录 GHCR，推送不可变的 commit SHA tag 与 `latest` tag。
4. 发布成功后清理较旧的 GHCR 镜像版本，只保留最近 3 个不同的 SHA 版本；`latest` 是当前版本的别名，不重复计为一个版本。若清理 API 权限/策略未配置，发布仍应成功并明确报告未执行清理，不得误删当前版本。
5. 不设置 Codespace self-hosted runner，不在 CD job 中部署服务。GHCR Container registry 的存储和流量按 [GitHub Packages 计费说明](https://docs.github.com/en/billing/concepts/product-billing/github-packages)现行政策暂时免费；保留策略用于限制版本数量及简化回滚，未来若计费政策变化再重新评估。

### 3.4 Codespace 按需手动部署

1. 用户启动现有 Codespace，确认端口 8080（app）和 8081（Keycloak）可见性为 **Private**。
2. 在仓库根目录运行 `bash scripts/deploy-codespaces-preview.sh [commit-sha]`；未提供 SHA 时使用最新发布版本。脚本从 Codespaces 环境变量推导 HTTPS app/Keycloak 地址，配置精确 OIDC redirect URI，并使用 Compose 拉取 GHCR 镜像。
3. GHCR package 若为 private，用户在 Codespace 本地使用只读 `read:packages` 凭据登录；凭据不得写入仓库、脚本或提交记录。公开 package 可匿名拉取，但只有确认镜像内容可公开时才可采用。
4. 脚本等待 `/actuator/health`，检查首页静态资源和 Keycloak OIDC issuer；之后用户在浏览器完成 operator 登录并验证 `/api/whoami`。
5. 成功部署后本地只保留正在运行的镜像和最多两个旧 SHA 镜像用于回滚。只清理未被容器使用的 TriageDeck app 镜像；不得自动运行带 `--volumes` 的 prune，也不得删除 `triagedeck_pgdata`。
6. 用户不再查看预览时停止 Codespace。停止会结束计算用量，但 Codespace 本身的存储仍按 [GitHub Codespaces 计费规则](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-codespaces/about-billing-for-github-codespaces)按 GB-month 计量；若长期不用，可先确认本地状态已提交/备份后再决定是否删除 Codespace。

### 3.5 环境变量与密钥

- `KEYCLOAK_ISSUER_URI`
- `KC_COLLECTOR_SECRET`
- GHCR 发布使用 workflow 的 `GITHUB_TOKEN` 与最小 `packages: write` 权限。
- 若私有 GHCR package 需要在 Codespace 拉取，使用用户本地只读 package 凭据；不需要为此创建常驻 runner secret。
- Codespaces app/Keycloak HTTPS 转发 URL 与精确 OIDC redirect URI 由部署脚本按当前 Codespace 生成。
- Keycloak 管理员密码和 GHCR 只读凭据仅保存在 Codespace 本地 secret/credential store，不得写入仓库或部署日志。
- 可选 `PREVIEW_PORT_FORWARDING_DOMAIN`；默认使用 Codespaces 的 `app.github.dev` 域。

---

## 4. 阶段性检查点与演示标准

| 检查点 | 对应 Milestone | 可看结果 | 验收命令示例 |
|--------|--------------|----------|-------------|
| CP1 | M0 | 环境可用 | `./gradlew --version`、`docker compose version` |
| CP2 | M1 | 单体可启动 | `docker compose up -d`、`curl /actuator/health` |
| CP3 | M2 | 业务端点可用 | `POST /api/alerts/ingest`、`GET /api/alerts/active`、`GET /api/audit/events` |
| CP4 | M3 | AI 与前端可用 | `POST /api/ai/briefing/{id}` (SSE)，前端首屏 |
| CP5 | M4 | 全流程演示 | `docker compose up -d --build`，8 分钟剧本 |
| CP6 | M1 / T-CICD-02 | Codespaces 私有预览 | main CI 成功并发布 SHA 镜像后，用户启动 Codespace 执行部署脚本；首页、health check、OIDC issuer 通过；浏览器 operator 登录并验证 `/api/whoami`；端口为 Private |

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
| v1.2 | 2026-11-25 | 为 M1 增加可核实的任务状态，记录 main push CI 失败与本地测试通过的差异，并明确先修复 CI、再完成 Codespaces 自动部署 | AI 辅助 |
| v1.3 | 2026-11-25 | 按免费额度优先调整 T-CICD-02：GH Actions 自动构建发布，Codespace 按需手动部署；去掉常驻 runner，规定 GHCR 与本地最多保留三版及安全清理边界；按用户确认标记 CI 完成 | AI 辅助 |

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