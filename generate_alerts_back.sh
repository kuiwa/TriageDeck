#!/usr/bin/env bash
set -euo pipefail

COUNT=${1:-5}
BASE_URL="${BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-triagedeck}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-ingest-service}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
export KEYCLOAK_CLIENT_ID KEYCLOAK_CLIENT_SECRET

R=$'\033[0;31m'; G=$'\033[0;32m'; Y=$'\033[1;33m'; B=$'\033[0;34m'; NC=$'\033[0m'
RESPONSE_FILE=""

cleanup() {
  [[ -z "$RESPONSE_FILE" ]] || rm -f -- "$RESPONSE_FILE"
}
trap cleanup EXIT

info() { echo -e "${B}[INFO]${NC} $1"; }
ok()   { echo -e "${G}[OK]${NC}   $1"; }
warn() { echo -e "${Y}[WARN]${NC} $1"; }

if ! [[ "$COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo -e "${R}错误: count 必须是正整数${NC}" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo -e "${R}错误: 缺少 curl${NC}" >&2
  exit 1
fi

if [[ -z "$AUTH_TOKEN" ]]; then
  if [[ -z "$KEYCLOAK_CLIENT_SECRET" ]]; then
    echo -e "${R}错误: 请设置 KEYCLOAK_CLIENT_SECRET（ingest-service 客户端密钥）${NC}" >&2
    exit 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${R}错误: 自动获取 token 需要 python3${NC}" >&2
    exit 1
  fi

  TOKEN_URL="${KEYCLOAK_URL%/}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"
  info "正在从 Keycloak 获取访问令牌..."
  if ! TOKEN_RESULT=$(
    python3 -c 'import os, sys, urllib.parse; sys.stdout.write(urllib.parse.urlencode({
      "grant_type": "client_credentials",
      "client_id": os.environ["KEYCLOAK_CLIENT_ID"],
      "client_secret": os.environ["KEYCLOAK_CLIENT_SECRET"],
    }))' |
      curl --silent --show-error --write-out '\n%{http_code}' \
        --request POST --header "Content-Type: application/x-www-form-urlencoded" \
        --data-binary @- "$TOKEN_URL"
  ); then
    echo -e "${R}错误: 无法连接 Keycloak token endpoint${NC}" >&2
    exit 1
  fi
  TOKEN_STATUS="${TOKEN_RESULT##*$'\n'}"
  TOKEN_BODY="${TOKEN_RESULT%$'\n'*}"
  if [[ "$TOKEN_STATUS" != "200" ]]; then
    echo -e "${R}错误: Keycloak 获取 token 失败（HTTP $TOKEN_STATUS）。请检查客户端密钥及 realm 配置。${NC}" >&2
    exit 1
  fi

  if ! AUTH_TOKEN=$(printf '%s' "$TOKEN_BODY" | python3 -c '
import json
import sys

try:
    token = json.load(sys.stdin).get("access_token")
    if not isinstance(token, str) or not token:
        raise ValueError("access_token missing")
except (OSError, ValueError, json.JSONDecodeError):
    raise SystemExit(1)

print(token)
'); then
    echo -e "${R}错误: Keycloak 响应中没有有效的 access_token${NC}" >&2
    exit 1
  fi
  info "已成功获取访问令牌（不会显示或写入磁盘）。"
fi

info "开始生成 $COUNT 条告警..."

S=0
F=0

for i in $(seq 1 "$COUNT"); do
  SRC="service-$i"
  TYP="pattern-$i"
  RID="resource-$i"
  EC="EVENT_$i"
  IDX=$(( (i - 1) % 5 ))
  case $IDX in
    0) SEV="CRITICAL" ;;
    1) SEV="HIGH" ;;
    2) SEV="MEDIUM" ;;
    3) SEV="LOW" ;;
    4) SEV="INFO" ;;
  esac

  TS=$(date -u -d "${i} minutes ago" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
  MSG="Alert #$i - $SRC / $TYP"
  DATA=$(printf '{"source":"%s","type":"%s","resourceId":"%s","eventCode":"%s","severity":"%s","message":"%s","occurredAt":"%s","payload":{}}' \
    "$SRC" "$TYP" "$RID" "$EC" "$SEV" "$MSG" "$TS")

  RESPONSE_FILE=$(mktemp)
  if STATUS=$(curl --silent --show-error --output "$RESPONSE_FILE" --write-out '%{http_code}' \
    --request POST "${BASE_URL%/}/api/alerts/ingest" \
    --header "Content-Type: application/json" \
    --header "Authorization: Bearer $AUTH_TOKEN" \
    --data "$DATA"); then
    if [[ "$STATUS" =~ ^2[0-9][0-9]$ ]]; then
      S=$((S + 1))
      ICON="✓"
      COLOR="$G"
    else
      F=$((F + 1))
      ICON="✗"
      COLOR="$R"
    fi
  else
    STATUS="curl error"
    F=$((F + 1))
    ICON="✗"
    COLOR="$R"
  fi

  printf "  %s%s${NC} [Alert %2d/%d] %s %s %s HTTP %s\n" \
    "$COLOR" "$ICON" "$((S + F))" "$COUNT" "$SEV" "$SRC" "$TYP" "$STATUS"
  rm -f -- "$RESPONSE_FILE"
  RESPONSE_FILE=""
done

echo ""
echo -e "${B}========================================${NC}"
echo -e "   结果摘要"
echo -e "${B}========================================${NC}"
printf "  总请求: %5d  成功: %5d  失败: %5d\n" "$COUNT" "$S" "$F"

if [[ "$F" -eq 0 ]]; then
  ok "所有 $COUNT 条请求成功!"
else
  warn "$F 条请求失败，请检查应用日志、Keycloak 配置和客户端权限。"
  exit 1
fi
