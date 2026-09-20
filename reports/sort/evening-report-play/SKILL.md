---
name: evening-report-play
description: >-
  Evening play leftover.
---

# evening-report-play

This folder **is** the runtime.

## How it fires

- Scheduler `_run("evening_report_play")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `reports-voice`.
