# Migrate `git-auto-push`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/scripts/auto-push.sh`, `ava-github-push.*`, `github-auto-push-toggle.sh`, Windows `auto-push.py`/`auto-pull.py`/`git_win.py` |
| To | `~/.ollama/skills/git-auto-push/scripts/` |
| Shim | Ava-Core `scripts/` execs these files |
| Secrets | Stay in Ava-Core `.env` — this script reads `GH_TOKEN` there, never copies it |
| systemd park | `references/systemd/ava-github-pull.*` — live OmniBook timer is `ava-auto-push.timer` |

Do not restore a second full copy under Ava-Core/scripts.
