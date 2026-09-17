---
name: broadcast-loop
description: >-
  OBS daily loop rotator (job broadcast-loop). OBS-gated.
---

# broadcast-loop

This folder **is** the runtime.

## How it fires

- Scheduler `_run("broadcast_loop")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `ava-ops`.
