---
name: merged-morning
description: >-
  10:20 HST merged morning summary queue (job merged-morning-summary).
---

# merged-morning

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
