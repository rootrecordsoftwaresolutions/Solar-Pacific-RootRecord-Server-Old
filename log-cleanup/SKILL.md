---
name: log-cleanup
description: >-
  Delete stale log files only (job log-cleanup).
---

# log-cleanup

This folder **is** the runtime.

## How it fires

- Scheduler `_run("log_cleanup")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `boot-idle-origin`.
