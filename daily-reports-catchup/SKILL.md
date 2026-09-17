---
name: daily-reports-catchup
description: >-
  14:00 HST mandatory-slot catch-up (job daily-reports-catchup).
---

# daily-reports-catchup

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("daily_reports_catchup")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
