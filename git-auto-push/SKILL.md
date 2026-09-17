---
name: git-auto-push
description: >-
  Linux GitHub auto-push timer and Windows git-sync helpers. Use when asked
  about ava-auto-push, github-auto-push-toggle, or ava-github-push.
---

# git-auto-push

This folder **is** the runtime. Do not dump tokens or `.env`.

## How it fires

- User timer `ava-auto-push.timer` → `scripts/auto-push.sh` → `scripts/ava-github-push.mjs`.
- Toggle: `scripts/github-auto-push-toggle.sh`.
- Ava-Core `scripts/` copies exec this folder.
- Windows `auto-push.py` / `auto-pull.py` / `git_win.py` live here too (not the OmniBook timer).

Never force-push. Never stage `.env`. Repo root is `/home/rootrecord/.ollama/skills/origin`.
