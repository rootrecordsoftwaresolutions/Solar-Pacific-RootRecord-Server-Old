---
name: morning-report
description: >-
  09:00 HST morning generate (job morning-report). Prelims then report_generation.
---

# morning-report

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
