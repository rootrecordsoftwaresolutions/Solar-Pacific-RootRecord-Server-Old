---
name: d1-sync
description: >-
  MySQL → Cloudflare D1 cache (job d1-sync).
---

# d1-sync

This folder **is** the runtime. Do not dump player counts or balances.

## How it fires

- Scheduler `_run("d1_sync")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `rootmc`.
