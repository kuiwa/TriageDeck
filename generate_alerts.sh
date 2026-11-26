#!/usr/bin/env bash
set -euo pipefail

# ========== Keycloak 配置区 ==========
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
REALM_NAME="triagedeck"
CLIENT_ID="ingest-service"
CLIENT_SECRET="Br772UsZFYYEntsV9Y0yz4lfPFAlP5Jw"
# =====================================

COUNT=${1:-5}
BASE_URL="${BASE_URL:-http://localhost:8080}"
# 外部传 AUTH_TOKEN 优先，否则自动拉取
AUTH_TOKEN="${AUTH_TOKEN:-}"

R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; NC='\033[0m'

print_flowchart() {
echo -e "${B}========================================${NC}"
echo -e "${B}   TriageDeck 告警生成流程${NC}"
echo -e "${B}========================================${NC}"
echo -e "  1. 启动: docker compose up -d"
echo -e "  2. 自动获取 Token: Keycloak client_credentials"
echo -e "  3. 运行: ./generate_alerts.sh [count]"
echo -e "  4. 验证: 数据库/API/前端查询"
echo -e "  5. 前端: 刷新 http://localhost:3000/alerts"
echo -e "${B}========================================${NC}"
echo ""
}

info() { echo -e "${B}[INFO]${NC} $1"; }
ok()   { echo -e "${G}[OK]${NC}   $1"; }
warn() { echo -e "${Y}[WARN]${NC} $1"; }

# 数据库查询辅助：通过 docker compose 进入 postgres 执行 SQL，返回单行结果。
# 查询失败时返回空字符串，避免 set -e 导致脚本提前退出。
db_query() {
    local result
    result=$(docker compose exec -T postgres psql -U triagedeck -d triagedeck -t -A -c "$1" 2>/dev/null) || true
    echo "$result"
}

# 查询单条告警详情：id|fingerprint|severity|last_seen_at|occurrence_count（制表符分隔）
db_detail() {
    docker compose exec -T postgres psql -U triagedeck -d triagedeck -t -A -F $'\t' -c \
        "SELECT id, fingerprint, severity, last_seen_at, occurrence_count FROM alerts_active WHERE source='${1}' AND type='${2}' AND resource_id='${3}' AND event_code='${4}' ORDER BY last_seen_at DESC LIMIT 1;" 2>/dev/null || true
}

# 自动获取token逻辑
if [ -z "$AUTH_TOKEN" ]; then
    info "未传入 AUTH_TOKEN，正在从 Keycloak 自动拉取 token..."
    if ! command -v jq &> /dev/null; then
        echo -e "${R}错误：需要 jq 解析json，请先安装 jq${NC}"
        exit 1
    fi
    TOKEN_RESP=$(curl -s --location "${KEYCLOAK_URL}/realms/${REALM_NAME}/protocol/openid-connect/token" \
        --header 'Content-Type: application/x-www-form-urlencoded' \
        --data-urlencode 'grant_type=client_credentials' \
        --data-urlencode "client_id=${CLIENT_ID}" \
        --data-urlencode "client_secret=${CLIENT_SECRET}")
    # 检查返回是否包含 access_token
    if echo "$TOKEN_RESP" | jq -r '.access_token' | grep -q null; then
        echo -e "${R}Keycloak 获取token失败！响应：${TOKEN_RESP}${NC}"
        exit 1
    fi
    AUTH_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.access_token')
    ok "Token 获取成功，有效期5分钟"
fi

if ! [[ "$COUNT" =~ ^[1-9][0-9]*$ ]]; then
echo -e "${R}错误: count 必须是正整数${NC}"; exit 1
fi

info "开始生成 $COUNT 条告警..."

S=0; F=0; DB_OK=0; DB_FAIL=0
ALERT_KEYS_FILE=$(mktemp)
trap 'rm -f "$ALERT_KEYS_FILE"' EXIT

for i in $(seq 1 "$COUNT"); do
RAND_SUFFIX="$RANDOM$RANDOM$(date +%s%N | cut -c7-13)"
SRC="service-${RAND_SUFFIX}-$i" TYP="pattern-${RAND_SUFFIX}-$i" RID="resource-${RAND_SUFFIX}-$i" EC="EVENT_${RAND_SUFFIX}_$i"
echo "${SRC}|${TYP}|${RID}|${EC}" >> "$ALERT_KEYS_FILE"
IDX=$(( (i-1) % 5 ))
case $IDX in
0) SEV="CRITICAL" ;;
1) SEV="HIGH" ;;
2) SEV="MEDIUM" ;;
3) SEV="LOW" ;;
4) SEV="INFO" ;;
esac
TS=$(date -u -d "${i} minutes ago" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
MSG="Alert #$i - $SRC / $TYP"
RF=$(mktemp)
HS=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/api/alerts/ingest" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -d "{\"source\":\"$SRC\",\"type\":\"$TYP\",\"resourceId\":\"$RID\",\"eventCode\":\"$EC\",\"severity\":\"$SEV\",\"message\":\"$MSG\",\"occurredAt\":\"$TS\",\"payload\":{}}" \
    -o "$RF" 2>/dev/null) || true
RO=$(cat "$RF"; rm -f "$RF")
HTTP_OK=0
if [ -n "$HS" ] && [ "$HS" -ge 200 ] && [ "$HS" -lt 400 ]; then
HTTP_OK=1
fi

# 数据库比对：用四段唯一键确认告警是否已入库 alerts_active
DB_ROW=$(db_query "SELECT count(*) FROM alerts_active WHERE source='${SRC}' AND type='${TYP}' AND resource_id='${RID}' AND event_code='${EC}';")
DB_ROW="${DB_ROW:-0}"
DB_OK_CHECK=0
if [ "$DB_ROW" -ge 1 ] 2>/dev/null; then
DB_OK_CHECK=1
else
    warn "[DB] 未确认写入 source=${SRC} type=${TYP} resource_id=${RID} event_code=${EC}"
fi

# DB 确认成功时，打印该条告警在库中的完整信息
if [ "$DB_OK_CHECK" -eq 1 ]; then
    DB_DETAIL=$(db_detail "$SRC" "$TYP" "$RID" "$EC")
    if [ -n "$DB_DETAIL" ]; then
        IFS=$'\t' read -r DB_ID DB_FP DB_SEV DB_LSA DB_CNT <<< "$DB_DETAIL"
        printf "      └─ DB: id=%s sev=%s last_seen=%s count=%s\n" "${DB_ID:0:8}..." "$DB_SEV" "$DB_LSA" "$DB_CNT"
    fi
fi

if [ "$HTTP_OK" -eq 1 ] && [ "$DB_OK_CHECK" -eq 1 ]; then
S=$((S+1)); DB_OK=$((DB_OK+1)); IC="✓"; ICOL="$G"
elif [ "$HTTP_OK" -eq 1 ] && [ "$DB_OK_CHECK" -eq 0 ]; then
F=$((F+1)); DB_FAIL=$((DB_FAIL+1)); IC="!"; ICOL="$Y"
else
F=$((F+1)); IC="✗"; ICOL="$R"
fi
printf "  ${ICOL}%c [Alert %2d/%d] %s %s %s${NC}\n" "$IC" "$((S+F))" "$COUNT" "$SEV" "$SRC" "$TYP"
done

echo ""
echo -e "${B}========================================${NC}"
echo -e "   结果摘要"
echo -e "${B}========================================${NC}"
printf "  总请求: %5d  API成功: %5d  API失败: %5d\n" "$COUNT" "$S" "$F"
printf "  库中确认: %5d  库中缺失: %5d\n" "$DB_OK" "$DB_FAIL"

echo ""
echo -e "${B}已入库告警明细：${NC}"
while IFS='|' read -r SRC TYP RID EC; do
    [ -z "$SRC" ] && continue
    DB_DETAIL=$(db_detail "$SRC" "$TYP" "$RID" "$EC")
    if [ -n "$DB_DETAIL" ]; then
        IFS=$'\t' read -r DB_ID DB_FP DB_SEV DB_LSA DB_CNT <<< "$DB_DETAIL"
        printf "  %-24s | %-8s | %-24s | count=%-3s | id=%s\n" "$SRC" "$DB_SEV" "$DB_LSA" "$DB_CNT" "${DB_ID:0:8}..."
    fi
done < "$ALERT_KEYS_FILE"
echo ""

if [ "$DB_FAIL" -eq 0 ] && [ "$F" -eq 0 ]; then
ok "所有 $COUNT 条请求成功且均已写入数据库"
elif [ "$DB_OK" -gt 0 ]; then
ok "$DB_OK 条已写入数据库，可刷新前端查看"
fi

if [ "$F" -gt 0 ]; then
warn "$F 条 API 请求失败"
if [ "$S" -gt 0 ]; then info "$S 条 HTTP 2xx，但未必入库"; fi
echo -e "${Y}原因: Token/应用/Keycloak"
fi

if [ "$DB_FAIL" -gt 0 ]; then
warn "$DB_FAIL 条 HTTP 成功但数据库未确认写入，可能尚未提交或命中去重冲突"
fi

echo ""
echo -e "${B}跨前端比对命令（先登录获取 token，再在终端执行）：${NC}"
echo -e "  curl -s -H \"Authorization: Bearer \$AUTH_TOKEN\" \"$BASE_URL/api/alerts/active?size=100\" | jq '.items[] | select(.source==\"service-1\")'"
echo -e "${B}========================================${NC}"
echo "脚本执行完毕"
