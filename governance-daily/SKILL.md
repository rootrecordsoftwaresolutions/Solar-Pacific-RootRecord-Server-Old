---
name: governance-daily
description: >-
  Daily RootRecord governance tally (job governance-daily).
---

# governance-daily

This folder **is** the runtime.

## How it fires

- Scheduler `_run("governance_daily")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `council-telegram`.
