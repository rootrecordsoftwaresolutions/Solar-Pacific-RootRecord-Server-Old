---
name: hourly-clip-reports
description: >-
  Hourly Kokoro desks + prebuild (jobs hourly-clip-reports, hourly-clip-prebuild).
  Ava Heart weather, Bruce Echo solar/host, Carly Nova Kīlauea / security / bandwidth. Live facts only.
---

# hourly-clip-reports

This folder **is** the runtime. Do not invent watts.

Clip-stitch is off. Skip a desk when that feed is down.

## How it fires

- Scheduler `_run("hourly_clip_reports")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
