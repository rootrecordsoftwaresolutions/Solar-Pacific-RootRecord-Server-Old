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

Never force-push. Never stage `.env`.

## One tree only

Vercel sites push from `~/.ollama/skills/<desk>/site/` with `site/.git` in place.
Do not create a second clone, mirror checkout, or `GIT_DIR` under `~/.local/state`.
Edit those `site/` folders — nowhere else.
