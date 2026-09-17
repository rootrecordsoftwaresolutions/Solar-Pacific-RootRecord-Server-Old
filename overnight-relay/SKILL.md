---
name: overnight-relay
description: >-
  Late-night relay snapshot (job overnight-relay).
---

# overnight-relay

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("overnight")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
