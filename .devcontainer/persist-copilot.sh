#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE="$WORKSPACE/.devcontainer/vscode-state"

has_vscode_lock() {
  local lock_root
  for lock_root in "$@"; do
    [ -d "$lock_root" ] || continue
    if [ -n "$(find "$lock_root" -type f -name vscode.lock -print -quit)" ]; then
      return 0
    fi
  done
  return 1
}

persist_dir() {
  local source="$1"
  local target="$2"
  local backup="${source}.pre-copilot-persistence"
  shift 2
  local -a lock_roots=("$@")

  if [ -L "$source" ]; then
    if [ "$(readlink -f -- "$source")" = "$(readlink -m -- "$target")" ]; then
      if [ -d "$backup" ]; then
        if has_vscode_lock "${lock_roots[@]}" "$source" "$backup"; then
          printf 'Leaving the original data at %s untouched while VS Code is active.\n' "$backup" >&2
          return 0
        fi
        cp -a -- "$backup/." "$target/"
        printf 'Reconciled persisted data from %s.\n' "$backup"
      fi
      return
    fi
    printf 'Refusing unexpected symlink at %s\n' "$source" >&2
    return 1
  fi

  if [ ! -d "$source" ]; then
    if [ -d "$target" ]; then
      if has_vscode_lock "${lock_roots[@]}" "$source" "$backup"; then
        printf 'Cannot restore %s while VS Code is active; rerun this script after stopping VS Code.\n' "$source" >&2
        return 0
      fi
      mkdir -p "$(dirname -- "$source")"
      ln -s -- "$target" "$source"
      printf 'Restored persisted data link at %s.\n' "$source"
    fi
    return 0
  fi
  if has_vscode_lock "${lock_roots[@]}" "$source" "$backup"; then
    printf 'Leaving %s untouched while VS Code is active; rerun this script after stopping VS Code.\n' "$source" >&2
    return 0
  fi
  if [ -e "$backup" ] || [ -L "$backup" ]; then
    printf 'Refusing to overwrite existing session data at %s or %s\n' "$target" "$backup" >&2
    return 1
  fi

  if [ -d "$target" ]; then
    mv -- "$source" "$backup"
    if ! ln -s -- "$target" "$source"; then
      mv -- "$backup" "$source"
      return 1
    fi
    printf 'Preserved the existing data at %s; using persisted data at %s.\n' "$backup" "$target"
    return 0
  fi
  if [ -e "$target" ] || [ -L "$target" ]; then
    printf 'Refusing to overwrite non-directory session data at %s\n' "$target" >&2
    return 1
  fi

  mkdir -p "$(dirname -- "$target")"
  cp -a -- "$source" "$target"
  mv -- "$source" "$backup"
  if ! ln -s "$target" "$source"; then
    mv -- "$backup" "$source"
    return 1
  fi
}

persist_file() {
  local source="$1"
  local target="$2"
  local backup="${source}.pre-copilot-persistence"
  shift 2
  local -a lock_roots=("$@")

  if [ -L "$source" ]; then
    if [ "$(readlink -f -- "$source")" = "$(readlink -m -- "$target")" ]; then
      return
    fi
    printf 'Refusing unexpected symlink at %s\n' "$source" >&2
    return 1
  fi

  if [ ! -f "$source" ]; then
    if [ -f "$target" ]; then
      if has_vscode_lock "${lock_roots[@]}"; then
        printf 'Cannot restore %s while VS Code is active; rerun this script after stopping VS Code.\n' "$source" >&2
        return 0
      fi
      mkdir -p "$(dirname -- "$source")"
      ln -s -- "$target" "$source"
      printf 'Restored persisted settings link at %s.\n' "$source"
    fi
    return 0
  fi
  if has_vscode_lock "${lock_roots[@]}"; then
    printf 'Leaving %s untouched while VS Code is active; rerun this script after stopping VS Code.\n' "$source" >&2
    return 0
  fi
  if [ -e "$backup" ] || [ -L "$backup" ]; then
    printf 'Refusing to overwrite existing settings at %s or %s\n' "$target" "$backup" >&2
    return 1
  fi

  if [ -f "$target" ]; then
    mv -- "$source" "$backup"
    if ! ln -s -- "$target" "$source"; then
      mv -- "$backup" "$source"
      return 1
    fi
    printf 'Preserved the existing settings at %s; using persisted settings at %s.\n' "$backup" "$target"
    return 0
  fi
  if [ -e "$target" ] || [ -L "$target" ]; then
    printf 'Refusing to overwrite non-file settings at %s\n' "$target" >&2
    return 1
  fi

  mkdir -p "$(dirname -- "$target")"
  cp -a -- "$source" "$target"
  mv -- "$source" "$backup"
  if ! ln -s "$target" "$source"; then
    mv -- "$backup" "$source"
    return 1
  fi
}

for entry in \
  "vscode-remote:$HOME/.vscode-remote/data/User" \
  "vscode-server:$HOME/.vscode-server/data/User" \
  "code:$HOME/.config/Code/User"
do
  key="${entry%%:*}"
  user_dir="${entry#*:}"
  [ -d "$(dirname -- "$user_dir")" ] || continue

  workspace_backup="${user_dir}/workspaceStorage.pre-copilot-persistence"
  persist_dir "$user_dir/workspaceStorage" "$STATE/$key/User/workspaceStorage" \
    "$user_dir/workspaceStorage" "$workspace_backup"
  persist_dir "$user_dir/globalStorage/github.copilot-chat" \
    "$STATE/$key/User/globalStorage/github.copilot-chat" \
    "$user_dir/workspaceStorage" "$workspace_backup"
done

persist_file "$HOME/.vscode-remote/data/Machine/settings.json" \
  "$STATE/vscode-remote/Machine/settings.json" \
  "$HOME/.vscode-remote/data/User/workspaceStorage" \
  "$HOME/.vscode-remote/data/User/workspaceStorage.pre-copilot-persistence"
