---
name: governance-boot
description: >-
  Once-on-boot governance snapshot. Never self-update here.
---

# governance-boot

This folder **is** the runtime.

## How it fires

- Scheduler `_run("governance_boot")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `council-telegram`.
