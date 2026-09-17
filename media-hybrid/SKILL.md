---
name: media-hybrid
description: Maps Media tree, hybrid daily reports, Core Ops report writers. Use when asked about Media paths, hybrid charge status, or Core Ops Reports.
---

# Media and hybrid reports

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

`hybrid-night-poller`, `hybrid-reports`, `media-library`, `youtube-download`. Media files stay under `$HOME/Media`. Dated hybrid notebooks live in `hybrid-reports/store/Reports`.

## Daily stamps

Hybrid inserts are `> ◇ **HHMM** —` lines above `+++Automation Cut Off`, plus replaced Weather Forecast / Kilauea Prediction sections. Skills `weather-kilauea` and `ecoflow-automations` keep a DAILY.md slice of those lines.
