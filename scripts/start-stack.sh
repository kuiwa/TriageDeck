#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker CLI is required." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is unavailable in this environment." >&2
  exit 1
fi

ALL_SERVICES=(postgres kafka keycloak app)

echo "========================================"
echo "  TriageDeck 容器部署向导"
echo "========================================"
echo ""

# ---------- 第 1 步：选择要部署的 container ----------
echo "请选择要部署的 container 服务："
echo "  1) 全部 (postgres, kafka, keycloak, app)"
echo "  2) 仅 postgres"
echo "  3) 仅 kafka"
echo "  4) 仅 keycloak"
echo "  5) 仅 app"
echo "  6) 自定义组合 (多选)"
echo ""
read -rp "请输入选项 [1-6] (默认 1): " SVC_CHOICE
SVC_CHOICE="${SVC_CHOICE:-1}"

SERVICES=()
case "$SVC_CHOICE" in
  1)
    SERVICES=(postgres kafka keycloak app)
    ;;
  2)
    SERVICES=(postgres)
    ;;
  3)
    SERVICES=(kafka)
    ;;
  4)
    SERVICES=(keycloak)
    ;;
  5)
    SERVICES=(app)
    ;;
  6)
    echo ""
    echo "可选服务："
    for i in "${!ALL_SERVICES[@]}"; do
      echo "  $((i+1))) ${ALL_SERVICES[$i]}"
    done
    echo ""
    read -rp "请输入服务编号，用空格分隔 (例如: 1 4): " -a SVC_IDXS
    SERVICES=()
    for idx in "${SVC_IDXS[@]}"; do
      case "$idx" in
        1) SERVICES+=(postgres) ;;
        2) SERVICES+=(kafka) ;;
        3) SERVICES+=(keycloak) ;;
        4) SERVICES+=(app) ;;
        *) echo "  忽略无效编号: $idx" ;;
      esac
    done
    if [[ ${#SERVICES[@]} -eq 0 ]]; then
      echo "未选择任何服务，改为全部部署。"
      SERVICES=(postgres kafka keycloak app)
    fi
    ;;
  *)
    echo "无效选项，改为全部部署。"
    SERVICES=(postgres kafka keycloak app)
    ;;
esac

echo ""
echo "已选择部署: ${SERVICES[*]}"
echo ""

# ---------- 第 2 步：是否停止已存在的 container ----------
read -rp "是否停止已存在的 container 后再启动？[Y/n] (默认 Y): " STOP_ANS
STOP_ANS="${STOP_ANS:-Y}"
STOP_EXISTING=1
if [[ "$STOP_ANS" =~ ^[Nn]$ ]]; then
  STOP_EXISTING=0
fi

# ---------- 第 3 步：是否 build 后端 ----------
read -rp "是否重新 build 后端 app 镜像？[y/N] (默认 N): " BUILD_ANS
BUILD_ANS="${BUILD_ANS:-N}"
DO_BUILD=0
if [[ "$BUILD_ANS" =~ ^[Yy]$ ]]; then
  DO_BUILD=1
fi

echo ""
echo "========================================"
echo "  部署配置确认"
echo "========================================"
echo "  服务: ${SERVICES[*]}"
echo "  停止已存在: $([[ "$STOP_EXISTING" -eq 1 ]] && echo "是" || echo "否")"
echo "  build 后端: $([[ "$DO_BUILD" -eq 1 ]] && echo "是" || echo "否")"
echo "========================================"
echo ""

# ---------- 执行 ----------
SERVICE_ARGS=()
for svc in "${SERVICES[@]}"; do
  SERVICE_ARGS+=("$svc")
done

if [[ "$STOP_EXISTING" -eq 1 ]]; then
  echo "正在停止已存在的 container: ${SERVICES[*]}"
  docker compose stop "${SERVICE_ARGS[@]}" || true
fi

if [[ "$DO_BUILD" -eq 1 ]]; then
  echo "正在 build 后端 app 镜像..."
  docker compose build app
fi

echo "正在启动 container: ${SERVICES[*]}"
docker compose up -d --wait --wait-timeout 240 "${SERVICE_ARGS[@]}"
docker compose ps

echo
echo "TriageDeck is ready:"
echo "  App health: http://localhost:8080/actuator/health"
echo "  Keycloak:   http://localhost:8081/"
