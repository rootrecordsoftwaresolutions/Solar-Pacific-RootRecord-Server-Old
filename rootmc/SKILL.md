---
name: rootmc
description: Maps Minecraft live detect, player economy (Gold), D1 cache, plugins. Use when asked about RootMC, play.rootmc.net, or in-game detect.
---

# RootMC

Carla. Stay in this folder. Lead with what the live code does now.

Public site: `site/` (`site/.git` → `Ava-Core-Dev/RootMC-Net`, Vercel project `rootmc-net`).
Edit and publish from `site/` only. `play.rootmc.net` stays on the game host; `api.rootmc.net` stays on the Worker; `app.rootmc.net` is Pages.

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

`minecraft-live`, `player-economy`, `d1-sync`, `user-qrcodes`, `account-import`, `rootmc-economy`, `rootmc-android`, `rootmc-mobile-web`. Routes, RCON, MySQL helpers still Ava-Core.
