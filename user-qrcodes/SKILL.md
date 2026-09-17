---
name: user-qrcodes
description: >-
  Per-user QR backfill (job user-qrcodes).
---

# user-qrcodes

This folder **is** the runtime. Do not dump player counts or balances.

## How it fires

- Scheduler `_run("user_qrcodes")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `rootmc`.
