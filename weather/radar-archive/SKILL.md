---
name: radar-archive
description: >-
  NWS Hawaii radar loop archive (job radar-archive). Use when asked how radar
  GIFs are stored.
---

# radar-archive

This folder **is** the runtime. Do not invent radar frames.

## How it fires

- Scheduler job id `radar-archive`, every **10 minutes** HST.
- **Skipped in night sleep**.
- `apps.core.services.radar_archive` execs `scripts/radar_archive.py`.
- Job wrapper: `scripts/job.py`.

Media/archive stays on disk, not in this skill.

Topic index: `weather-kilauea`.
