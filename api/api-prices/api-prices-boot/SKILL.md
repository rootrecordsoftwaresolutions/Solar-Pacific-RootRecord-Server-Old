---
name: api-prices-boot
description: >-
  Once-on-boot public API price refresh.
---

# api-prices-boot

This folder **is** the runtime.

## How it fires

- Scheduler `_run("api_prices_boot")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `public-edge`.
