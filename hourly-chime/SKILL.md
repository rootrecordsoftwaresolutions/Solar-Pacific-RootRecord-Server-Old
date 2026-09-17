---
name: hourly-chime
description: >-
  Half-hourly time chime (job time-chime).
---

# hourly-chime

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("hourly_chime")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
