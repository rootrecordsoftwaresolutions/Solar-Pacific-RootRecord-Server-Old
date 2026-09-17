---
name: inbox-drain
description: >-
  Cloudflare offline inbox drain (job inbox-drain).
---

# inbox-drain

This folder **is** the runtime. Do not dump balances, ads, or player counts.

## How it fires

- Scheduler `_run("inbox_drain")` loads `scripts/job.py` (AdSense/AdMob use eod helpers that import the shim).
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `public-edge`.
