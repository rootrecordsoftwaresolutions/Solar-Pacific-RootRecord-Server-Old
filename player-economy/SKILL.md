---
name: player-economy
description: >-
  Player economy snapshot + multiplier (job player-economy-report).
---

# player-economy

This folder **is** the runtime. Do not dump player counts or balances.

## How it fires

- Scheduler `_run("player_economy")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `rootmc`.
