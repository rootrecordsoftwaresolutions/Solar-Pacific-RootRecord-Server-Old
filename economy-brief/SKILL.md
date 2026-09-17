---
name: economy-brief
description: >-
  Daily economy brief file + Discord (job economy-brief).
---

# economy-brief

This folder **is** the runtime. Do not dump balances, ads, or player counts.

## How it fires

- Scheduler `_run("economy_brief")` loads `scripts/job.py` (AdSense/AdMob use eod helpers that import the shim).
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `public-edge`.
