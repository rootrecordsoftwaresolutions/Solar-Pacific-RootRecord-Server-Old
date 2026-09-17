---
name: minecraft-live
description: >-
  RootMC live detect / OBS (job minecraft-live).
---

# minecraft-live

This folder **is** the runtime. Do not dump player counts or balances.

## How it fires

- Scheduler `_run("minecraft_live")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `rootmc`.
