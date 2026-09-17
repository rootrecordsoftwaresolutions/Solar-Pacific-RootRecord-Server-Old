---
name: hurricane-tracker
description: >-
  Worldwide tropical cyclone fetch/state (refresh_storms, OBS kit). Use when asked how storms are listed or hurricane OBS mode works.
---

# hurricane-tracker

This folder **is** the runtime. Do not invent storm names.

## How it fires

- Processor `scripts/hurricane_tracker.py`. Tracks: `scripts/storm_track.py` (RAMMB history + official forecast, JTWC MOVING, region vs Hawaiʻi).
- Legacy job wrapper `scripts/job.py` (same as fetch).
- `apps.core.services.hurricane_tracker` execs the processor.
**Skipped in night sleep**. Hawaiʻi OBS board is storms inside 800 nmi only. Far WestPac systems stay on the world board with region + toward/away.

Topic index: `weather-kilauea`.
