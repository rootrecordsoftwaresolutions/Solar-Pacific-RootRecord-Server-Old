---
name: api-prices
description: >-
  Daily public API price capture (job api-prices).
---

# api-prices

This folder **is** the runtime. Do not dump balances, ads, or player counts.

## How it fires

- Scheduler `_run("api_prices")` loads `scripts/job.py` (AdSense/AdMob use eod helpers that import the shim).
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `public-edge`.
