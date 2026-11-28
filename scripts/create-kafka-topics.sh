#!/usr/bin/env bash
set -euo pipefail

# 本地开发（app / 模拟器跑在宿主机）用：在 Homebrew Kafka 上显式创建 4 个 topic。
# 全栈容器场景由 docker compose 的 kafka-init 负责，不需要执行本脚本。
#
# 用法：
#   ./scripts/create-kafka-topics.sh
#   KAFKA_BIN=/opt/homebrew/opt/kafka/bin ./scripts/create-kafka-topics.sh
#   KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./scripts/create-kafka-topics.sh

KAFKA_BIN="${KAFKA_BIN:-/usr/local/opt/kafka/bin}"
BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
PARTITIONS="${KAFKA_TOPIC_PARTITIONS:-1}"
REPLICATION="${KAFKA_TOPIC_REPLICATION:-1}"

TOPICS=(alert.ingested alert.cleared alert.ingested.dlt alert.cleared.dlt)

if [[ ! -x "${KAFKA_BIN}/kafka-topics" ]]; then
  echo "找不到 ${KAFKA_BIN}/kafka-topics（用 KAFKA_BIN=... 指定 Kafka 安装目录）" >&2
  exit 1
fi

for topic in "${TOPICS[@]}"; do
  "${KAFKA_BIN}/kafka-topics" --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists --topic "${topic}" \
    --partitions "${PARTITIONS}" --replication-factor "${REPLICATION}" >/dev/null
  echo "ok ${topic}"
done

echo "--- ${BOOTSTRAP} 当前 topic ---"
"${KAFKA_BIN}/kafka-topics" --bootstrap-server "${BOOTSTRAP}" --list
