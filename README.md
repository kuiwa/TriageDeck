# TriageDeck

> 告警分诊台 + 统一审计的控制台。单进程 Spring Boot 应用（模块化单体），前端静态资源打进同一个 jar，
> Docker Compose 起四容器（app / keycloak / postgres / kafka）。

**当前状态：文档阶段（S0–S5 完成，待评审）。代码尚未开始。**

## 从哪读起

| 你想知道 | 读这个 |
|---|---|
| 需求原文（US 编号） | `docs/01-requirements.md` |
| 本期到底做哪些（含 3 条可选的取舍、需求勘误） | `docs/03-scope-v1.md` |
| 整体设计、不变量、端点地图 | `docs/02-design.md` |
| 字段/枚举/错误码/表结构/接口入参返回（唯一真相） | `docs/02b-contract.md` |
| 某模块怎么实现 | `docs/lld/01-auth.md` … `docs/lld/05-platform-notify.md` |
| 机器可读接口 | `docs/api/openapi.yaml` |
| 数据库 ER、索引、造数、T01 校验清单 | `docs/db/schema-v1.md` |
| 每个任务干什么、怎么验收 | `docs/ai-tasks.md` |
| 为什么这样决策 | `docs/adr/0001…0010` |
| 每条需求落在哪 | `docs/traceability.md` |

总入口：`docs/00-index.md`。硬约束：`AGENTS.md`。

## 技术栈

Java 21 / Spring Boot 3.5.x（虚拟线程）、PostgreSQL 16 + Flyway、Kafka（KRaft 单节点）、
Keycloak（OIDC + PKCE）、Casbin（jcasbin）、Spring AI（1.0.x）、
React 19 + TypeScript + Vite + TanStack Query + Tailwind。

## 本地开发一键启动

在仓库根目录分别打开两个终端：

```bash
./scripts/start-stack.sh      # 启动 app / Keycloak / PostgreSQL / Kafka，等待健康检查
./scripts/start-frontend.sh   # 首次自动 npm ci，然后启动 Vite
```

前端默认访问 `http://localhost:5173`。启动脚本可从任意工作目录调用；停止前端按 `Ctrl+C`，停止容器运行 `docker compose down`（不要加 `-v`，以保留 PostgreSQL 数据）。

## 静态演示模式（无需后端，适合 Cloudflare Pages 等纯静态托管）

设置环境变量 `VITE_MOCK_MODE=true` 后，前端跳过 Keycloak 登录，直接使用内置模拟数据渲染告警列表与严重度看板，适合离线静态展示：

```bash
cd frontend
VITE_MOCK_MODE=true npm run dev   # 开发模式，访问 http://localhost:5173
VITE_MOCK_MODE=true npm run build # 生产构建，产物在 frontend/dist，可部署到任意静态托管
```

模拟模式下：
- 不需要 Keycloak、PostgreSQL、Kafka 等后端服务；
- 顶部显示"模拟演示模式"徽标，并标注"模拟数据"；
- 清除操作被禁用（仅演示，不执行真实状态变更）。

## 快速开始（T13 完成后填实；当前命令仅为目标态）

```bash
cp .env.example .env
docker compose up -d --build
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

## 二期清单

见 `docs/03-scope-v1.md §3`（B7 自动清除 / C6 审计哈希链 / D3 交接摘要 等）。

## 本仓库的文档纪律

1. 字段只写在 `02b-contract.md`；其他文档引用小节号。
2. 改契约先写 ADR，再改契约，再改 LLD，最后改代码。
3. 每册 LLD 的「待确认项」评审通过后整行删除，结论进正文。
