#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: npm is required to start the frontend." >&2
  exit 1
fi

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  cd "$FRONTEND_DIR"
  npm ci
else
  cd "$FRONTEND_DIR"
fi

exec npm run dev -- --host 0.0.0.0
