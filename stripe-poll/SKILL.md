---
name: stripe-poll
description: >-
  Stripe snapshot refresh (job stripe-poll).
---

# stripe-poll

This folder **is** the runtime. Do not dump balances, ads, or player counts.

## How it fires

- Scheduler `_run("stripe_poll")` loads `scripts/job.py` (AdSense/AdMob use eod helpers that import the shim).
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `public-edge`.
