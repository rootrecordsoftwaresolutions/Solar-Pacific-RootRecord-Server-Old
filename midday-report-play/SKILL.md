---
name: midday-report-play
description: >-
  12:05 HST midday WAV play (job midday-report-play).
---

# midday-report-play

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("midday_report_play")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
