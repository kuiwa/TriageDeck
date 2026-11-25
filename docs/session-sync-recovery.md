# VS Code 会话历史恢复备忘录

> 注意：历史记录能否跨 Rebuild Container 保留尚未验证。

记录时间：2026-11-24

## 已配置

- 用户设置中的 `chat.sessionSync.enabled` 和 `github.copilot.chat.localIndex.enabled` 均为 `true`；有效状态仍需检查 VS Code 是否受组织策略管理。
- 用户设置文件也链接到了工作区持久目录，容器重建后应继续保留这些设置。
- `workspaceStorage`、Copilot Chat 的 `globalStorage` 和 Machine 设置通过软链接保存到 `.devcontainer/vscode-state/`。
- `.devcontainer/vscode-state/` 已加入 `.gitignore`，不要提交会话数据。
- `.devcontainer/devcontainer.json` 的 `postCreateCommand` 和 `postStartCommand` 会调用 `.devcontainer/persist-copilot.sh`。

## 当前迁移状态

首次复制和软链接已经完成。迁移时 VS Code 仍持有原存储的锁，因此原目录被保留为备份，没有删除：

- `~/.vscode-remote/data/User/workspaceStorage.pre-copilot-persistence`
- `~/.vscode-remote/data/User/globalStorage/github.copilot-chat.pre-copilot-persistence`
- `~/.vscode-remote/data/Machine/settings.json.pre-copilot-persistence`

脚本检测到旧存储中的 `vscode.lock` 时，会保留备份并输出 `Leaving the original data ... while VS Code is active`；锁消失后会将备份内容合并到持久目录，并输出 `Reconciled persisted data ...`。

## Stop/Start 后的状态（2026-11-24）

已执行 Stop Codespace 后再 Start Codespace，用户确认聊天记录仍可见。随后检查确认：

- `workspaceStorage`、Copilot Chat `globalStorage` 和 Machine `settings.json` 的活动路径均指向 `/workspaces/TriageDeck/.devcontainer/vscode-state/`。
- 持久目录中的对应数据均存在。
- 检查时活动 `workspaceStorage` 下没有 `vscode.lock`。
- 原始 `.pre-copilot-persistence` 备份仍保留，暂不删除。

**现在可以 Rebuild Container。** Rebuild 前后都不要删除 `.devcontainer/vscode-state/`；重建完成后确认聊天记录仍可见。若重建后记录未显示，先查看本备忘录和备份，不要清理持久目录或原始备份。

## 下次操作顺序

以上 Stop/Start 步骤已完成。本次下一步是 **Rebuild Container**。若之后需要再次迁移旧数据：

1. 等待容器启动完成，查看 `postStartCommand` 输出。
2. 如果仍看到 `Leaving the original data ... while VS Code is active`，先不要重建，也不要手动删除锁文件或备份；旧副本尚未合并。
3. 确认出现 `Reconciled persisted data ...` 且没有迁移错误后，再继续重建。

## 重新连接后检查

确认存储路径指向持久目录：

```bash
readlink "$HOME/.vscode-remote/data/User/workspaceStorage"
readlink "$HOME/.vscode-remote/data/User/globalStorage/github.copilot-chat"
readlink "$HOME/.vscode-remote/data/Machine/settings.json"
```

预期目标都位于 `/workspaces/TriageDeck/.devcontainer/vscode-state/`。在 VS Code Copilot 状态栏确认会话同步状态；若本地历史索引缺失，可运行 `/chronicle:reindex`。

## Rebuild 后记录再次不可见

检查发现持久目录中的 Copilot Chat `session-store.db` 仍存在，但容器内的 `globalStorage/github.copilot-chat` 源路径缺失。旧脚本在源路径缺失时直接返回，没有重新建立软链接，因此 VS Code 看不到持久数据。脚本现会在目标数据存在且没有活动锁时恢复链接；若源路径与持久目录同时存在，会把源路径保留为 `.pre-copilot-persistence` 备份并以持久目录为准，不会静默覆盖数据。

若检测到 `vscode.lock`，脚本只跳过相关路径并给出提示，不移动目录或改写设置文件。先正常关闭 VS Code，再重新运行持久化脚本；不要手动删除锁文件。`postStartCommand` 先执行持久化恢复，避免环境检查失败时跳过恢复步骤。

## Rebuild 后复查（2026-11-24）

- 用户确认 Copilot session sync 由组织管理且未启用；个人设置中的 `true` 不能覆盖该策略。
- 活动存储链接仍指向 `.devcontainer/vscode-state/`，但持久化的 `session-store.db` 中 `sessions`、`turns` 和 `search_index` 均为空；预期的 `.pre-copilot-persistence` 备份路径未找到。
- 因此无法确认历史记录会在 Rebuild 后保留；当前检查到的本地数据库也没有可恢复的会话。不要删除持久目录。请组织管理员确认是否能启用 session sync；即使启用，也需另行确认旧会话是否仍在云端。

## 相关文件

- `.devcontainer/devcontainer.json`
- `.devcontainer/persist-copilot.sh`
- `.gitignore`
