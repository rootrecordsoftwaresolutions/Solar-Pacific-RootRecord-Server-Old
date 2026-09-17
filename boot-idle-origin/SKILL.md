---
name: boot-idle-origin
description: Maps AVA Console launch, idle-stop, origin :8787, Ollama, feature toggles, and systemd units. Use when asked about boot, desk idle, recycle origin, or what starts at login.
---

# Boot, idle, origin

Carla. Stay in this folder. Lead with what the live code does now.

## Keep current

After changing this topic, from Ava-Core run:

```bash
python3 .cursor/skills/ecosystem-index/scripts/refresh-all.py
```

This skill folder is the ops desk (`~/.ollama/skills`). Open it first. `desk/src` and `desk/ops` are
maps to related files. Runners live in `~/.ollama/skills/<fn>/scripts/`. `DAILY.md` is the processed day. `INDEX.md`
lists the live paths. `references/migrate.md` is the move checklist. `CURRENT.md` is the generated map.

Ava-Core / Core Ops copies are shims.

## How to answer

1. Follow `desk/` instead of hunting the repo.
2. Clock times are HST unless a file says UTC.
3. Night sleep (`Ecoflow/state/night-mode.json` `sleeping`) skips Ava scheduler jobs.
4. Historical Windows/OptiPlex paths are history only — see `ecosystem-history`.

## Function desks already moved

`launch`, `idle-stop`, `log-cleanup`, `logs`, `state`, `database`, `code-review`, `boot-prelims`, `day-board-boot`, `recycle-origin`, `ollama-env`, `companions`, `ensure-ava-runtime`, `ollama-lifecycle`, `ollama-client`, `uptime-log`. Origin FastAPI is the `origin` skill (`ns/apps` import shims).
