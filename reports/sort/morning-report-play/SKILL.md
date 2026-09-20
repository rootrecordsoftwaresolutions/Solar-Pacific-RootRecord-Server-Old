---
name: morning-report-play
description: >-
  09:05 HST morning WAV play (job morning-report-play).
---

# morning-report-play

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
