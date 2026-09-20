---
name: reports-voice
description: Maps morning/midday/late reports, clip packs, chimes, OBS/radio toggles, and spoken audio. Use when asked about reports, voice, chimes, or MP3 play.
---

# Reports and voice

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

`morning-report`, `morning-report-play`, `merged-morning`, `midday-report`, `midday-report-play`, `late-report`, `late-report-play`, `hourly-chime`, `remaining-tasks`, `morning-boot-replay`, `hourly-clip-reports`, `report-readiness`, `report-periodic-audio`, `day-reports-morning`, `day-reports-midday`, `day-reports-evening`, `overnight-relay`, `daily-reports-catchup`, `cursor-fallback`, `report-generation`, `reports`, `daily-report-board`, `startup-voice`, `report-audio-manual`, `report-blog`, `voice-events`, `synth`, `kokoro`, `xai`, `model-pick`. Generate/play libraries live in those desks.
