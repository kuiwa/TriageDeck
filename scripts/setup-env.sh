#!/usr/bin/env bash
# TriageDeck 环境一次性安装 / 校验脚本（codespace 重启或本地切换后运行）
# 作用：固定 JAVA_HOME 为 Java 21，校验 gradle / docker compose / node 可用，并给出验收命令。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> [1/4] 固定 JAVA_HOME 为 Java 21"
if [ -d /usr/local/sdkman/candidates/java/21.0.13.0+2-ms ]; then
  export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.13.0+2-ms
elif [ -d /usr/local/sdkman/candidates/java/21 ]; then
  export JAVA_HOME=/usr/local/sdkman/candidates/java/21
elif command -v sdk >/dev/null 2>&1; then
  sdk use java 21.0.13.0+2-ms 2>/dev/null || true
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

echo "==> [2/4] 校验 Gradle Wrapper"
./gradlew --version >/dev/null 2>&1 && echo "   ./gradlew --version 正常（$(./gradlew --version 2>/dev/null | grep '^Gradle ' | awk '{print $2}')）" || echo "   !! ./gradlew 异常"

echo "==> [3/4] 校验 Docker Compose"
docker compose version >/dev/null 2>&1 && echo "   docker compose 正常：$(docker compose version | awk '{print $3}')" || echo "   !! docker compose 不可用"

echo "==> [4/4] 校验 Node"
node --version >/dev/null 2>&1 && echo "   node 正常：$(node --version)" || echo "   !! node 不可用"

echo ""
echo "========== 验收命令（复制即用）========"
echo "cd $REPO_ROOT"
echo "export JAVA_HOME=$JAVA_HOME"
echo "./gradlew --version            # 应输出 Gradle 8.14.1"
echo "./gradlew build                # BUILD SUCCESSFUL"
echo "docker compose version         # compose v2 正常输出"
echo "======================================"
