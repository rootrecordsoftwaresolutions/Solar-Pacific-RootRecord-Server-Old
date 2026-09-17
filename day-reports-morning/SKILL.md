---
name: day-reports-morning
description: >-
  Morning slot extra kinds (job day-reports-morning).
---

# day-reports-morning

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("day_reports_morning")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
