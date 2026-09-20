---
name: day-reports-midday
description: >-
  Midday slot extra kinds (job day-reports-midday).
---

# day-reports-midday

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("day_reports_midday")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
