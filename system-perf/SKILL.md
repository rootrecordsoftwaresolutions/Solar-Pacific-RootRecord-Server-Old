---
name: system-perf
description: >-
  Host CPU/RAM/disk snapshot (job system-performance).
---

# system-perf

This folder **is** the runtime.

## How it fires

- Scheduler `_run("system_perf")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `ava-ops`.
