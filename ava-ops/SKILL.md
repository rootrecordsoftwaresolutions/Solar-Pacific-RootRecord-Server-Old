---
name: ava-ops
description: Maps Ava Ops Android, Bluetooth RFCOMM bridge, ops API, desktop desk. Use when asked about the phone app, BT, mobile-dashboard, or Idle desk.
---

# Ava Ops and Bluetooth

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

`android-sdk`, `kilauea-alerts`, `rootmc-android`, `system-perf`, `broadcast-loop`, `broadcast`, `feature-toggles`, `python-drop-runner`, `xmrig`, `net-gate`, `obs-studio`, `radio`. Phone Gradle root is this skill `android/`. Origin ops routes still Ava-Core.
