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

docker compose up -d --wait --wait-timeout 240
docker compose ps

echo
echo "TriageDeck is ready:"
echo "  App health: http://localhost:8080/actuator/health"
echo "  Keycloak:   http://localhost:8081/"
