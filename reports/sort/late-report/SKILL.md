---
name: late-report
description: >-
  21:00 / 23:30 HST late generate (jobs late-report, late-final-report).
---

# late-report

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("late_report")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
