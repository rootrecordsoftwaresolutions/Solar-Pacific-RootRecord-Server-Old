---
name: boot-prelims
description: >-
  Boot prelims: NOAA, NWS, Kīlauea, then Boot Report file.
---

# boot-prelims

This folder **is** the runtime.

## How it fires

- Scheduler `_run("boot_prelims")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `boot-idle-origin`.
