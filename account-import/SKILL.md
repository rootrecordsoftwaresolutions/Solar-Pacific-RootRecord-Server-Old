---
name: account-import
description: >-
  Identity import from disk/D1/Discord/RootMC (job account-import).
---

# account-import

This folder **is** the runtime. Do not dump player counts or balances.

## How it fires

- Scheduler `_run("account_import")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `rootmc`.
