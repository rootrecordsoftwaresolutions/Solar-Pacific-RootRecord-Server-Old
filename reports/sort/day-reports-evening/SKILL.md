---
name: day-reports-evening
description: >-
  Evening slot extra kinds (job day-reports-evening).
---

# day-reports-evening

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("day_reports_evening")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
