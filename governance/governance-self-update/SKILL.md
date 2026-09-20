---
name: governance-self-update
description: >-
  Governance self-update after boot grace (job governance-self-update).
---

# governance-self-update

This folder **is** the runtime.

## How it fires

- Scheduler `_run("governance_self_update")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `council-telegram`.
