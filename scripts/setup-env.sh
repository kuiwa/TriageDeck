#!/usr/bin/env bash
# TriageDeck Codespaces startup verification.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'ERROR: required command is missing: %s\n' "$1" >&2
    exit 1
  fi
}

echo "==> Checking Java 21"
require_command java
java_major="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ { print $2; exit }')"
if [[ "$java_major" != "21" ]]; then
  printf 'ERROR: expected Java 21, found %s\n' "${java_major:-unknown}" >&2
  exit 1
fi
echo "   Java $java_major"

echo "==> Checking Node.js 20+"
require_command node
node_version="$(node --version)"
node_major="${node_version#v}"
node_major="${node_major%%.*}"
if (( node_major < 20 )); then
  printf 'ERROR: expected Node.js 20+, found %s\n' "$node_version" >&2
  exit 1
fi
echo "   Node.js $node_version"

echo "==> Checking Gradle Wrapper"
if [[ ! -x ./gradlew ]]; then
  echo "ERROR: ./gradlew is missing or not executable" >&2
  exit 1
fi
gradle_output="$(./gradlew --version 2>&1)"
gradle_version="$(printf '%s\n' "$gradle_output" | awk '/^Gradle / { print $2; exit }')"
if [[ -z "$gradle_version" ]]; then
  echo "ERROR: Gradle Wrapper did not report a Gradle version" >&2
  exit 1
fi
echo "   Gradle $gradle_version"

echo "==> Checking Docker Compose"
require_command docker
compose_version="$(docker compose version)"
echo "   $compose_version"

echo "==> Checking Git LFS and repairing repository hooks"
require_command git-lfs
lfs_version="$(git lfs version)"
git lfs install --local
echo "   $lfs_version"
echo "   Repository Git LFS hooks are installed"
