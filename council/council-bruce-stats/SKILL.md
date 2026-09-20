---
name: council-bruce-stats
description: >-
  Bruce measured desk sample (job council-bruce-stats).
---

# council-bruce-stats

This folder **is** the runtime.

## How it fires

- Scheduler `_run("council_bruce_stats")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `council-telegram`.
