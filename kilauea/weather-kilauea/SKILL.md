---
name: weather-kilauea
description: Maps NWS/NOAA, county stitch, Kīlauea, earthquakes, hurricane desk. Use when asked about weather, volcano, USGS, NHC, or storm radio.
---

# Weather, Kīlauea, storms

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

`rr-kilauea`, `rr-noaa`, `nws-hawaii`, `earthquake-hourly`, `radar-archive`, `official-weather-media`, `hurricane-tracker`, `hurricane-desk`, `hurricane-fetch`, `hurricane-radio`, `hurricane-obs`, `nhc-media`, `kilauea-cams`, `council-quake`, `live-wx`, `geography`, `kilauea-alerts` — runners live in those skills. This topic is the grouping.

## Hybrid daily lines

Every 30 minutes (`solar-notes-quarter-hour`) the hybrid notebook gets the same `> ◇ **HHMM** —` stamps used for charge/power. Weather one-liners, weather-window sections, and Kīlauea status go there — not a separate jsonl.

Read [DAILY.md](DAILY.md) (sliced from today's hybrid file). Live copy is `~/.ollama/skills/hybrid-reports/store/Reports/.../hybrid-manual-daily-report-YYYY-MM-DD.md`. Current files: `Media/documents/reports/nws-hawaii-counties-current.md`, `kilauea-current.md`.
