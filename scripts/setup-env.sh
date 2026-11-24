#!/usr/bin/env bash
# TriageDeck 环境一次性安装 / 校验脚本（codespace 重启或本地切换后运行）
# 作用：固定 JAVA_HOME 为 Java 21，校验 gradle / docker compose / node 可用，并给出验收命令。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> [1/5] 安装 Git LFS（解决 pre-push 钩子依赖，使 codespace 可 git push）"
if ! command -v git-lfs >/dev/null 2>&1; then
  if command -v apk >/dev/null 2>&1; then
    apk add --no-cache git-lfs >/dev/null 2>&1 && echo "   git-lfs 已安装（apk）：$(git lfs version | head -1)" || echo "   !! git-lfs 安装失败（apk）"
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update >/dev/null 2>&1 && apt-get install -y git-lfs >/dev/null 2>&1 && echo "   git-lfs 已安装（apt）：$(git lfs version | head -1)" || echo "   !! git-lfs 安装失败（apt）"
  else
    echo "   !! 未找到 apk/apt-get，无法自动安装 git-lfs"
  fi
else
  echo "   git-lfs 已存在：$(git lfs version | head -1)"
fi

echo "==> [2/5] 固定 JAVA_HOME 为 Java 21"
if [ -d /usr/local/sdkman/candidates/java/21.0.13.0+2-ms ]; then
  export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.13.0+2-ms
elif [ -d /usr/local/sdkman/candidates/java/21 ]; then
  export JAVA_HOME=/usr/local/sdkman/candidates/java/21
elif command -v sdk >/dev/null 2>&1; then
  sdk use java 21.0.13.0+2-ms 2>/dev/null || true
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

echo "==> [3/5] 校验 Gradle Wrapper"
./gradlew --version >/dev/null 2>&1 && echo "   ./gradlew --version 正常（$(./gradlew --version 2>/dev/null | grep '^Gradle ' | awk '{print $2}')）" || echo "   !! ./gradlew 异常"

echo "==> [4/5] 校验 Docker Compose"
docker compose version >/dev/null 2>&1 && echo "   docker compose 正常：$(docker compose version | awk '{print $3}')" || echo "   !! docker compose 不可用"

echo "==> [5/5] 校验 Node"
node --version >/dev/null 2>&1 && echo "   node 正常：$(node --version)" || echo "   !! node 不可用"

echo ""
echo "========== 验收命令（复制即用）========"
echo "cd $REPO_ROOT"
echo "export JAVA_HOME=$JAVA_HOME"
echo "./gradlew --version            # 应输出 Gradle 8.14.1"
echo "./gradlew build                # BUILD SUCCESSFUL"
echo "docker compose version         # compose v2 正常输出"
echo "======================================"
