---
name: earthquake-hourly
description: >-
  Hourly HI + global EQ clip report and M≥2 poll. Use when asked how earthquake
  hourly or earthquake-m2-poll run.
---

# earthquake-hourly

This folder **is** the runtime. Do not invent magnitudes.

## How it fires

- Job `earthquake-hourly` on the hour HST.
- Job `earthquake-m2-poll` every 5 minutes (same `run(reason="poll")`).
- **Skipped in night sleep**.
- `apps.core.services.earthquake_hourly` execs `scripts/earthquake_hourly.py`.

Clip-stitch WAV. No Grok TTS.

Topic index: `weather-kilauea`.
