#!/usr/bin/env bash
# codespace 磁盘清理脚本
# - Docker 镜像：按创建时间保留最新 3 个，其余删除（正在使用的镜像删除失败会被忽略）
# - Docker 构建缓存、停止容器、无用网络/卷：全部清理
# 用法：./scripts/cleanup.sh [--dry-run]
set -euo pipefail

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
fi

run() {
  if $DRY_RUN; then
    echo "[dry-run] $*"
  else
    echo "[run] $*"
    "$@"
  fi
}

echo "================ 清理前 ================"
df -h /workspaces / 2>/dev/null | tail -n +2
echo "--- Docker 资源占用 ---"
docker system df 2>/dev/null || true

echo ""
echo "================ 开始清理 ================"

# 1. 清理构建缓存（通常占用最大，可安全回收）
run docker builder prune -f

# 2. 清理已停止的容器
run docker container prune -f

# 3. 清理无用网络
run docker network prune -f

# 4. 清理无用卷
run docker volume prune -f

# 5. 镜像：按创建时间倒序，保留最新 3 个，其余删除
#    获取所有非 dangling 镜像的 ID，按 CreatedAt 降序排列
echo "--- 处理 Docker 镜像（保留最新 3 个）---"
mapfile -t ALL_IMAGE_IDS < <(docker images --format "{{.ID}}|{{.CreatedAt}}" \
  | sort -t'|' -k2 -r \
  | cut -d'|' -f1)

KEEP=3
IDX=0
for img in "${ALL_IMAGE_IDS[@]}"; do
  IDX=$((IDX + 1))
  if [ "$IDX" -le "$KEEP" ]; then
    echo "保留 ($IDX/3): $img"
  else
    run docker rmi -f "$img" 2>/dev/null || echo "  (跳过：正在使用或不存在) $img"
  fi
done

# 6. 再次 prune 干净残留的 dangling 镜像
run docker image prune -f

echo ""
echo "================ 清理后 ================"
df -h /workspaces / 2>/dev/null | tail -n +2
echo "--- Docker 资源占用 ---"
docker system df 2>/dev/null || true
echo "--- 剩余镜像 ---"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedAt}}" 2>/dev/null || true
echo ""
echo "清理完成。"