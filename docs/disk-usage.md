# Codespace 磁盘使用记录

> 用途：记录 Codespace 工作区磁盘与主要可清理项，供后续对比。Docker 和目录统计采用不同计量口径，不能将各表数值直接相加；`df` 是文件系统总用量的准值。

## 2026-11-25 清理记录

采集环境：当前 Codespace，挂载点 `/workspaces`。清理前采集于 `03:09:59 UTC`，清理后采集于 `03:10:31 UTC`。

### 文件系统

| 指标 | 清理前 | 清理后 | 变化 |
|---|---:|---:|---:|
| 容量 | 32 GB | 32 GB | — |
| 已用 | 13 GB | 8.8 GB | 约 -4.2 GB |
| 可用 | 18 GB | 21 GB | 约 +3 GB（显示值经过四舍五入） |
| 使用率 | 42% | 30% | -12 个百分点 |

### Docker 占用

| 类别 | 清理前 | 清理后 | 操作/说明 |
|---|---:|---:|---|
| 镜像 | 2.757 GB，6 个；165 MB 可回收 | 2.597 GB，5 个；5.175 MB 可回收 | 删除未被容器使用的 `triagedeck:codespaces-preview-check`；约回收 160 MB。标称镜像大小 640 MB，因层共享并未全部释放 |
| BuildKit 构建缓存 | 4.961 GB，87 项；4.162 GB 可回收 | 799.2 MB，9 项；159.9 MB 可回收 | 执行 `docker builder prune --force`；实际清理 4.162 GB |
| 容器可写层 | 237.8 MB，4 个停止容器 | 237.8 MB，4 个停止容器 | 未删除，避免丢失容器可写层中的本地状态 |
| Docker volumes | 48.93 MB，4 个 active | 48.93 MB，4 个 active | 未删除；包含 `triagedeck_pgdata` |

停止容器：`triagedeck-app-1`（83.8 MB）、`triagedeck-keycloak-1`（153 MB）、`triagedeck-postgres-1`（20.5 kB）、`triagedeck-kafka-1`（1.23 MB）。容器可写层虽然被 Docker 标记为可回收，但本次保留；删除 Keycloak 容器可能丢失容器层中的本地状态。PostgreSQL 数据卷必须保留。

### 工作区与用户目录（清理后）

| 路径 | 占用 | 备注 |
|---|---:|---|
| `/workspaces/TriageDeck` | 396 MB | 仓库总量 |
| └ `.devcontainer/vscode-state` | 228 MB | 会话/本地状态；保留 |
| └ `frontend` | 约 83 MB | 其中 `node_modules` 82 MB；可重建，但删除后需重新安装依赖 |
| └ `app` | 81 MB | 项目文件 |
| └ `.git` | 3.6 MB | Git 历史 |
| `/home/vscode` | 1.4 GB | 用户目录总量 |
| └ `.gradle` | 721 MB | 构建缓存；清除后 Gradle 需重新下载依赖 |
| └ `.vscode-remote` | 425 MB | VS Code Server/扩展相关；删除可能触发重新安装 |
| └ `.npm` | 147 MB | npm 缓存；可重建，但可能需重新下载 |

目录子项已经包含在父目录总量中，不应重复相加。Docker 的镜像虚拟大小、共享层和构建缓存也有各自的计量规则。

## 后续清理建议

| 优先级 | 项目 | 建议 |
|---|---|---|
| 高 | BuildKit 构建缓存 | 需要空间时可再次运行 `docker builder prune`；会降低后续构建速度，但不删除镜像、容器或 volume |
| 按需 | 停止的测试容器 | 先确认容器可写层不含需保留状态，再按名称移除；不要直接批量清除 |
| 低 | npm/Gradle 缓存、`node_modules` | 空间紧张时可删除，后续会重新下载或安装 |
| 保留 | `triagedeck_pgdata`、其他 Docker volumes | 不运行带 `--volumes` 的 prune |
| 保留 | `.devcontainer/vscode-state` | 保存 Codespace 本地会话/状态 |

## 清理命令记录

```bash
docker builder prune --force
docker image rm triagedeck:codespaces-preview-check
```

本次未执行容器清理、volume 清理、仓库文件清理或用户目录缓存清理。
