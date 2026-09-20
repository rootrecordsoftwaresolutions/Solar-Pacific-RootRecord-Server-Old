---
name: admob-eod
description: >-
  AdMob file snapshot (job admob-eod).
---

# admob-eod

This folder **is** the runtime. Do not dump balances, ads, or player counts.

## How it fires

- Scheduler `_run("admob_report")` loads `scripts/job.py` (AdSense/AdMob use eod helpers that import the shim).
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `public-edge`.
