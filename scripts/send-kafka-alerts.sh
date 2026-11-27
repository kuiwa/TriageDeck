#!/usr/bin/env bash
set -euo pipefail

# 一键向 TriageDeck 的 alert.ingested 投告警（交互或参数）。
#
# 交互：
#   ./scripts/send-kafka-alerts.sh
# 非交互（CI / 演示）：
#   ./scripts/send-kafka-alerts.sh --count=10 --severity=HIGH --type=threshold --yes
#   ./scripts/send-kafka-alerts.sh --count=10 --severity=MIXED --type=link --bootstrap=localhost:19092 -y
#
# broker 约定：
#   localhost:19092 → docker compose 的 Kafka（容器里的 app 在消费）
#   localhost:9092  → Homebrew Kafka（宿主机 ./gradlew :app:bootRun 在消费）

TOPIC="${KAFKA_TOPIC_INGESTED:-alert.ingested}"
CLEARED_TOPIC="${KAFKA_TOPIC_CLEARED:-alert.cleared}"
DLT_TOPIC="${TOPIC}${KAFKA_DLT_SUFFIX:-.dlt}"
GROUP="${KAFKA_GROUP_INGEST:-triagedeck-alert-ingest}"
SOURCE="${KAFKA_SOURCE:-device-simulator}"
COUNT=""
SEVERITY=""
TYPE=""
RESOURCES=""
BOOTSTRAP=""
ASSUME_YES=0
DOCTOR=0
VERIFY=1
VERIFY_TIMEOUT="${KAFKA_VERIFY_TIMEOUT:-30}"

SEVERITY_CHOICES=(CRITICAL HIGH MEDIUM LOW INFO)

usage() {
  sed -n '3,12p' "$0" | sed 's/^# \{0,1\}//'
  echo
  echo "可选参数："
  echo "  --count=N          条数（默认 10）"
  echo "  --severity=X       CRITICAL|HIGH|MEDIUM|LOW|INFO|MIXED（MIXED=轮转混投）"
  echo "  --type=X           threshold|link|http|自定义字符串"
  echo "  --resources=N      资源个数（默认 3；count>N 时自然产生同 fingerprint 重复上报）"
  echo "  --bootstrap=H:P    指定 broker（不填则自动探测 19092 / 9092）"
  echo "  -y, --yes          跳过确认"
  echo "  --no-verify        只发送，不做「生产/消费/落库」校验"
  echo "  --verify-timeout=N 等待消费落库的秒数（默认 30）"
  echo "  --doctor           只体检链路（消费组位点/滞后、DLT、库内计数、最近告警），不发送"
  echo "  -h, --help         显示本帮助"
}

for arg in "$@"; do
  case "$arg" in
    --count=*) COUNT="${arg#*=}" ;;
    --severity=*) SEVERITY="${arg#*=}" ;;
    --type=*) TYPE="${arg#*=}" ;;
    --resources=*) RESOURCES="${arg#*=}" ;;
    --bootstrap=*) BOOTSTRAP="${arg#*=}" ;;
    -y|--yes) ASSUME_YES=1 ;;
    --no-verify) VERIFY=0 ;;
    --verify-timeout=*) VERIFY_TIMEOUT="${arg#*=}" ;;
    --doctor) DOCTOR=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$arg（用 --help 查看）" >&2; exit 2 ;;
  esac
done

detect_kafka_bin() {
  if [[ -n "${KAFKA_BIN:-}" ]]; then
    printf '%s' "$KAFKA_BIN"
  elif command -v kafka-console-producer >/dev/null 2>&1; then
    dirname "$(command -v kafka-console-producer)"
  elif [[ -x /usr/local/opt/kafka/bin/kafka-console-producer ]]; then
    printf '%s' /usr/local/opt/kafka/bin
  elif [[ -x /opt/homebrew/opt/kafka/bin/kafka-console-producer ]]; then
    printf '%s' /opt/homebrew/opt/kafka/bin
  else
    echo "找不到 kafka-console-producer，请设置 KAFKA_BIN=/path/to/kafka/bin" >&2
    exit 1
  fi
}

KAFKA_BIN="$(detect_kafka_bin)"

port_open() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1
}

if [[ -z "$BOOTSTRAP" ]]; then
  CANDIDATES=()
  port_open 19092 && CANDIDATES+=("localhost:19092")
  port_open 9092 && CANDIDATES+=("localhost:9092")
  if [[ ${#CANDIDATES[@]} -eq 0 ]]; then
    echo "19092 / 9092 都连不上，请先启动 Kafka（start-stack 或 brew services start kafka）" >&2
    exit 1
  elif [[ ${#CANDIDATES[@]} -eq 1 ]]; then
    BOOTSTRAP="${CANDIDATES[0]}"
    echo "broker: ${BOOTSTRAP}（自动探测）"
  else
    echo "检测到多个 broker："
    echo "  1) localhost:19092  → docker compose Kafka（容器 app 消费）"
    echo "  2) localhost:9092   → Homebrew Kafka（宿主 bootRun 消费）"
    if [[ "$ASSUME_YES" -eq 1 || ! -t 0 ]]; then
      BOOTSTRAP="localhost:19092"
      echo "非交互模式，默认使用 localhost:19092（可用 --bootstrap= 覆盖）"
    else
      read -rp "选择 [1]: " broker_choice
      case "${broker_choice:-1}" in
        2) BOOTSTRAP="localhost:9092" ;;
        *) BOOTSTRAP="localhost:19092" ;;
      esac
    fi
  fi
fi

broker_port() {
  printf '%s' "$BOOTSTRAP" | awk -F: '{print $NF}'
}

if ! port_open "$(broker_port)"; then
  echo "broker ${BOOTSTRAP} 连不上：Kafka 没起来（start-stack / brew services start kafka），或端口填错" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Kafka CLI 是不可信的（broker 卡住时会无限重试），统一加墙钟超时
with_timeout() {
  local seconds="$1"; shift
  perl -e 'alarm shift; exec @ARGV' "$seconds" "$@"
}

# topic 所有分区 end offset 之和（用于生产/死信计数）；拿不到时返回空串
topic_offsets() {
  local out
  out="$(with_timeout 20 "${KAFKA_BIN}/kafka-get-offsets" --bootstrap-server "$BOOTSTRAP" --topic "$1" --time -1 2>/dev/null || true)"
  if [[ -z "$out" ]]; then
    printf ''
    return 0
  fi
  printf '%s\n' "$out" | awk -F: '{sum += $3} END {print sum + 0}'
}

group_describe() {
  with_timeout 20 "${KAFKA_BIN}/kafka-consumer-groups" --bootstrap-server "$BOOTSTRAP" --describe --group "$GROUP" 2>/dev/null || true
}

# 消费组在 alert.ingested 上的 current/log-end/lag（tab 分隔）
group_row() {
  group_describe | awk -v t="$TOPIC" '$2 == t {print $4 "\t" $5 "\t" $6}' | head -1
}

group_has_members() {
  local out
  out="$(group_describe)"
  [[ -n "$out" ]] && ! grep -q "has no active members" <<<"$out"
}

# 查库：优先 PSQL_BIN，其次 docker compose 里的 postgres
db_query() {
  local sql="$1"
  if [[ -n "${PSQL_BIN:-}" ]]; then
    "${PSQL_BIN}" "${DB_URL:-postgresql://triagedeck:triagedeck@localhost:5432/triagedeck}" -tAc "$sql" 2>/dev/null && return 0
  fi
  (cd "$REPO_ROOT" && docker compose exec -T postgres psql \
    -U "${DB_USERNAME:-triagedeck}" -d "${DB_NAME:-triagedeck}" -tAc "$sql" 2>/dev/null) && return 0
  return 1
}

doctor() {
  echo "========== 链路体检 =========="
  echo "broker : ${BOOTSTRAP}"
  local ingested_end dlt_end
  ingested_end="$(topic_offsets "$TOPIC")"
  dlt_end="$(topic_offsets "$DLT_TOPIC")"
  echo "topic  : ${TOPIC}（end=${ingested_end:-拿不到}） / ${DLT_TOPIC}（end=${dlt_end:-拿不到}）"
  local row current logend lag members_note
  row="$(group_row)"
  if [[ -z "$row" ]]; then
    echo "消费组 : ${GROUP} 不存在（还没有实例消费过这个 topic）"
  else
    current="$(printf '%s' "$row" | cut -f1)"
    logend="$(printf '%s' "$row" | cut -f2)"
    lag="$(printf '%s' "$row" | cut -f3)"
    members_note="有活跃成员"
    group_has_members || members_note="无活跃成员"
    echo "消费组 : ${GROUP} ${members_note}，committed=${current} log-end=${logend} lag=${lag}"
  fi
  local health
  health="$(curl -s --max-time 3 http://localhost:8080/actuator/health 2>/dev/null || true)"
  if [[ -n "$health" ]]; then
    echo "app    : ${health}"
  else
    echo "app    : localhost:8080 不通（容器 app 与宿主 bootRun 都没起）"
  fi
  echo "--- 数据库 ---"
  if ! db_query "select 1" >/dev/null; then
    echo "查不到数据库（compose 的 postgres 没起，且本机没有 psql）"
    echo "============================="
    return 0
  fi
  echo "active_alerts   : $(db_query 'select count(*) from alerts_active')"
  echo "alert_event     : 总 $(db_query 'select count(*) from alert_event')，KAFKA 入口 $(db_query "select count(*) from alert_event where ingress='KAFKA'")"
  echo "processed_event : $(db_query 'select count(*) from processed_event')"
  echo "最近 5 条活动告警："
  db_query "select resource_id || ' | ' || severity || ' | count=' || occurrence_count || ' | ' || to_char(last_seen_at, 'HH24:MI:SS') from alerts_active order by last_seen_at desc limit 5" \
    | sed 's/^/  /'
  echo "============================="
}

# 端到端校验：生产位点 → 消费位点/lag → 库内 fingerprint/证据链/幂等台账 → 死信
verify_pipeline() {
  local from="$1" to="$2" sent_at="$3" dlt_from="$4"
  local current="" logend="" lag="" consume_ok=0 waited=0 row
  while (( waited <= VERIFY_TIMEOUT )); do
    row="$(group_row)"
    if [[ -n "$row" ]]; then
      current="$(printf '%s' "$row" | cut -f1)"
      logend="$(printf '%s' "$row" | cut -f2)"
      lag="$(printf '%s' "$row" | cut -f3)"
      if [[ "$current" =~ ^[0-9]+$ && "$lag" =~ ^[0-9]+$ ]] \
          && (( current >= to )) && (( lag == 0 )); then
        consume_ok=1
        break
      fi
    fi
    sleep 2
    waited=$((waited + 2))
  done

  local members_note="有活跃成员"
  group_has_members || members_note="无活跃成员"

  local dlt_to active_row active_hit="" active_sum event_count processed_count fps_sql fp
  dlt_to="$(topic_offsets "$DLT_TOPIC")"
  fps_sql=""
  for fp in "${FINGERPRINTS[@]:-}"; do fps_sql="${fps_sql}'${fp}',"; done
  fps_sql="${fps_sql%,}"
  active_row=""
  if [[ -n "$fps_sql" ]]; then
    active_row="$(db_query "select count(*) || '|' || coalesce(sum(occurrence_count), 0) from alerts_active where fingerprint in (${fps_sql})" || true)"
  fi
  if [[ -n "$active_row" ]]; then
    active_hit="${active_row%%|*}"
    active_sum="${active_row##*|}"
  fi
  event_count="$(db_query "select count(*) from alert_event where ingress = 'KAFKA' and received_at >= '${sent_at}'" || true)"
  processed_count="$(db_query "select count(*) from processed_event where consumer_group = '${GROUP}' and topic = '${TOPIC}' and record_offset >= ${from} and record_offset < ${to}" || true)"

  echo
  echo "========== 端到端校验 =========="
  if [[ -n "$to" ]] && (( to > from )); then
    echo "✓ 生产：${TOPIC} 位点 ${from} → ${to}（+$((to - from))）"
  else
    echo "✗ 生产：${TOPIC} 位点没有增长（${from} → ${to:-?}），消息没进 broker"
  fi
  if (( consume_ok == 1 )); then
    echo "✓ 消费：${GROUP} committed=${current} lag=${lag}（${members_note}）"
  else
    echo "✗ 消费：${GROUP} committed=${current:-?} lag=${lag:-?}（${members_note}）"
    if [[ "$members_note" == "无活跃成员" ]]; then
      echo "   → 没有消费者在跑：容器 app 没起 / 镜像是旧的 / 宿主 bootRun 没起。先 docker compose up -d --build app"
    else
      echo "   → 有成员但位点不前进：docker compose logs --tail=120 app | grep -E 'kafka_ingest|ERROR'"
    fi
  fi
  if [[ -z "$active_row" ]]; then
    echo "? 落库：查不到数据库，跳过（psql / compose postgres 都不可用）"
  elif [[ -n "$active_hit" ]] && (( active_hit > 0 )); then
    echo "✓ 落库：alerts_active 命中 ${active_hit}/${#FINGERPRINTS[@]} 个 fingerprint，occurrence_count 合计 ${active_sum}；alert_event(KAFKA) 新增 ${event_count:-?}；processed_event 新增 ${processed_count:-?}"
  else
    echo "✗ 落库：alerts_active 一个 fingerprint 都没命中（校验失败会进 DLT，看下面死信与日志）"
  fi
  if [[ "$dlt_to" =~ ^[0-9]+$ && "$dlt_from" =~ ^[0-9]+$ ]]; then
    if (( dlt_to > dlt_from )); then
      echo "✗ 死信：${DLT_TOPIC} ${dlt_from} → ${dlt_to}（$((dlt_to - dlt_from)) 条解析/校验失败，看 app 日志）"
    else
      echo "✓ 死信：${DLT_TOPIC} 没有新增（${dlt_to}）"
    fi
  fi
  echo "================================"
  if (( consume_ok == 1 )) && [[ -n "$active_hit" ]] && (( active_hit > 0 )); then
    echo "结论：链路通，前端刷新即可看到"
    return 0
  fi
  echo "结论：链路有问题，按上面 ✗ 的提示排查；随时可跑 $0 --doctor 看状态"
  return 1
}

if [[ "$DOCTOR" -eq 1 ]]; then
  doctor
  exit 0
fi

choose_severity() {
  echo "告警等级："
  echo "  1) CRITICAL"
  echo "  2) HIGH"
  echo "  3) MEDIUM"
  echo "  4) LOW"
  echo "  5) INFO"
  echo "  6) MIXED（轮转混投，演示看板用）"
  read -rp "选择 [2]: " severity_choice
  case "${severity_choice:-2}" in
    1) SEVERITY=CRITICAL ;;
    3) SEVERITY=MEDIUM ;;
    4) SEVERITY=LOW ;;
    5) SEVERITY=INFO ;;
    6) SEVERITY=MIXED ;;
    *) SEVERITY=HIGH ;;
  esac
}

choose_type() {
  echo "告警类型："
  echo "  1) threshold（阈值类：CPU / 内存 / 磁盘）"
  echo "  2) link（链路类）"
  echo "  3) http（HTTP 5xx）"
  echo "  4) 自定义"
  read -rp "选择 [1]: " type_choice
  case "${type_choice:-1}" in
    2) TYPE=link ;;
    3) TYPE=http ;;
    4) read -rp "输入类型名（字母数字，如 db_timeout）: " custom_type; TYPE="${custom_type:-custom}" ;;
    *) TYPE=threshold ;;
  esac
}

read_number() {
  local prompt="$1" default="$2" value
  read -rp "${prompt} [${default}]: " value
  value="${value:-$default}"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]]; then
    echo "需要正整数" >&2
    exit 2
  fi
  printf '%s' "$value"
}

if [[ "$ASSUME_YES" -eq 1 || ! -t 0 ]]; then
  COUNT="${COUNT:-10}"
  RESOURCES="${RESOURCES:-3}"
  SEVERITY="${SEVERITY:-HIGH}"
  TYPE="${TYPE:-threshold}"
fi

if [[ -z "$SEVERITY" ]]; then choose_severity; fi
SEVERITY="$(printf '%s' "$SEVERITY" | tr '[:lower:]' '[:upper:]')"
case "$SEVERITY" in
  CRITICAL|HIGH|MEDIUM|LOW|INFO|MIXED) ;;
  *) echo "非法 --severity=$SEVERITY" >&2; exit 2 ;;
esac

if [[ -z "$TYPE" ]]; then choose_type; fi

if [[ -z "$COUNT" ]]; then COUNT="$(read_number "投递条数" 10)"; fi
if ! [[ "$COUNT" =~ ^[0-9]+$ ]] || [[ "$COUNT" -lt 1 ]]; then
  echo "非法 --count=$COUNT" >&2
  exit 2
fi

if [[ -z "$RESOURCES" ]]; then RESOURCES="$(read_number "资源个数（count>N 时同 fingerprint 重复上报）" 3)"; fi
if ! [[ "$RESOURCES" =~ ^[0-9]+$ ]] || [[ "$RESOURCES" -lt 1 ]]; then
  echo "非法 --resources=$RESOURCES" >&2
  exit 2
fi

# 与 alert 模块 Fingerprint.of 同意：字段 strip+lowercase，0x1F 连接，SHA-256 小写十六进制
normalize() {
  printf '%s' "$1" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | tr '[:upper:]' '[:lower:]'
}

fingerprint() {
  printf '%s\x1F%s\x1F%s\x1F%s' \
    "$(normalize "$1")" "$(normalize "$2")" "$(normalize "$3")" "$(normalize "$4")" \
    | shasum -a 256 | awk '{print $1}'
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

event_code_for() {
  local type="$1" slot="$2"
  case "$type" in
    threshold) case "$slot" in 1) echo CPU_HIGH ;; 2) echo MEMORY_HIGH ;; *) echo DISK_HIGH ;; esac ;;
    link) echo LINK_DOWN ;;
    http) echo HTTP_5XX ;;
    *) printf '%s' "$type" | tr '[:lower:]' '[:upper:]' ;;
  esac
}

severity_for() {
  local index="$1"
  if [[ "$SEVERITY" == "MIXED" ]]; then
    printf '%s' "${SEVERITY_CHOICES[$((index % 5))]}"
  else
    printf '%s' "$SEVERITY"
  fi
}

FINGERPRINTS=()
add_fingerprint() {
  local fp="$1" existing
  for existing in "${FINGERPRINTS[@]:-}"; do
    [[ "$existing" == "$fp" ]] && return 0
  done
  FINGERPRINTS+=("$fp")
}

build_batch() {
  local index slot resource event severity metric message key
  for ((index = 0; index < COUNT; index++)); do
    slot=$((index % RESOURCES + 1))
    # 资源id带上 index，保证同一批次内每条告警 fingerprint 都不同，
    # 落库时走 insert 新行（而非 on-conflict 只累加 occurrence_count），
    # 这样前端活动告警表里能直观看到新增行数。
    resource="device-${slot}-${index}"
    event="$(event_code_for "$TYPE" "$slot")"
    severity="$(severity_for "$index")"
    metric=$((70 + (index * 7) % 30))
    message="${resource} ${TYPE} metric ${metric}%"
    key="$(fingerprint "$SOURCE" "$TYPE" "$resource" "$event")"
    add_fingerprint "$key"
    printf '%s\t{"source":"%s","type":"%s","resourceId":"%s","eventCode":"%s","severity":"%s","message":"%s","occurredAt":"%s","payload":{"metric":%d}}\n' \
      "$key" \
      "$(json_escape "$SOURCE")" "$(json_escape "$TYPE")" "$resource" "$event" "$severity" \
      "$(json_escape "$message")" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$metric"
  done
}

echo
echo "========== 发送计划 =========="
echo "  broker : ${BOOTSTRAP}"
echo "  topic  : ${TOPIC}"
echo "  等级   : ${SEVERITY}"
echo "  类型   : ${TYPE}"
echo "  条数   : ${COUNT}（资源 ${RESOURCES} 个，同 fingerprint 会累加 occurrence_count）"
echo "  校验   : 生产 / 消费 / 落库 / 死信（--no-verify 关闭，--doctor 只体检）"
echo "=============================="
if [[ "$ASSUME_YES" -ne 1 ]]; then
  read -rp "确认发送？[Y/n]: " confirm
  case "${confirm:-Y}" in
    [Nn]*) echo "已取消"; exit 0 ;;
  esac
fi

before="0"
if [[ -x "${KAFKA_BIN}/kafka-get-offsets" ]]; then before="$(topic_offsets "$TOPIC")"; fi
dlt_before="0"
if [[ -x "${KAFKA_BIN}/kafka-get-offsets" ]]; then dlt_before="$(topic_offsets "$DLT_TOPIC")"; fi
sent_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

messages_file="$(mktemp)"
trap 'rm -f "$messages_file"' EXIT
build_batch > "$messages_file"

if ! with_timeout 90 "${KAFKA_BIN}/kafka-console-producer" \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC" \
  --property parse.key=true \
  --producer-property max.block.ms=8000 \
  --producer-property delivery.timeout.ms=10000 \
  --producer-property request.timeout.ms=5000 \
  < "$messages_file" >/dev/null; then
  echo "投递失败或超时（90s）：broker ${BOOTSTRAP} 没响应，先用 --doctor 看状态" >&2
  exit 1
fi

after="$(topic_offsets "$TOPIC")"

echo "已投递 ${COUNT} 条 → ${TOPIC} @ ${BOOTSTRAP}"
echo "topic 位点：${before} → ${after}"

exit_code=0
if [[ "$VERIFY" -eq 1 ]]; then
  verify_pipeline "$before" "$after" "$sent_at" "$dlt_before" || exit_code=1
else
  echo "（--no-verify：跳过消费/落库校验）"
fi
exit "$exit_code"
