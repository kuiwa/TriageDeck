#!/usr/bin/env python3
# gen_script.py - 生成 generate_alerts.sh 脚本

script_content = """#!/usr/bin/env bash
set -euo pipefail
COUNT=${1:-5}
BASE_URL="${BASE_URL:-http://localhost:8080}"
AUTH_TOKEN="${AUTH_TOKEN:-}"

R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; NC='\033[0m'

print_flowchart() {
  echo -e "${B}========================================${NC}"
  echo -e "${B}   TriageDeck 告警生成流程${NC}"
  echo -e "${B}========================================${NC}"
  echo -e "  1. 启动: docker compose up -d"
  echo -e "  2. 获取 Token: Keycloak 浏览器登录获取 JWT"
  echo -e "  3. 运行: AUTH_TOKEN=xxx ./generate_alerts.sh [count]"
  echo -e "  4. 验证: 数据库/API/前端查询"
  echo -e "  5. 前端: 刷新 http://localhost:3000/alerts"
  echo -e "${B}========================================${NC}"
  echo ""
}

info() { echo -e "${B}[INFO]${NC} $1"; }
ok()   { echo -e "${G}[OK]${NC}   $1"; }
warn() { echo -e "${Y}[WARN]${NC} $1"; }

if [ -z "$AUTH_TOKEN" ]; then
  print_flowchart
  echo -e "${R}错误: 未提供 AUTH_TOKEN${NC}"
  echo -e "${Y}获取 token: Keycloak auth endpoint${NC}"
  exit 1
fi

if ! [[ "$COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo -e "${R}错误: count 必须是正整数${NC}"; exit 1
fi

info "开始生成 $COUNT 条告警..."

S=0; F=0

for i in $(seq 1 "$COUNT"); do
  SRC="service-$i" TYP="pattern-$i" RID="resource-$i" EC="EVENT_$i"
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
    -H "Content-Type: application/json" -H "Authorization: Bearer $AUTH_TOKEN" \
    -d "{\"source\":\"$SRC\",\"type\":\"$TYP\",\"resourceId\":\"$RID\",\"eventCode\":\"$EC\",\"severity\":\"$SEV\",\"message\":\"$MSG\",\"occurredAt\":\"$TS\",\"payload\":{}}" -o "$RF" 2>/dev/null)
  RO=$(cat "$RF"; rm -f "$RF")
  HSC=$(echo "$RO" | grep -oP '\b\d{3}\b' | head -1)
  if [ -n "$HSC" ] && [ "$HSC" -ge 200 ] && [ "$HSC" -lt 400 ]; then
    S=$((S+1)); IC="✓"; ICOL="$G"
  else
    F=$((F+1)); IC="✗"; ICOL="$R"
  fi
  printf "  %s%c [Alert %2d/%d] %s%s %s\n" "$IC" "$ICOL" "$SEV" "$((S+F))" "$SRC" "$TYP" "$i"
done

echo ""
echo -e "${B}========================================${NC}"
echo -e "   结果摘要"
echo -e "${B}========================================${NC}"
printf "  总请求: %5d  成功: %5d  失败: %5d\n" "$COUNT" "$S" "$F"
echo ""

if [ "$F" -eq 0 ]; then
  ok "所有 $COUNT 条请求成功!"
  echo -e "${Y}验证: 1) docker exec ... 2) curl ... 3) 刷新前端"
else
  warn "$F 条失败"
  if [ "$S" -gt 0 ]; then info "$S 条可能已写入，可查库"; fi
  echo -e "${Y}原因: Token/应用/Keycloak"
fi
echo -e "${B}========================================${NC}"
echo "脚本执行完毕"
"""
 
with open('/workspaces/TriageDeck/generate_alerts.sh', 'w') as f:
    f.write(script_content)

print('Script written successfully')