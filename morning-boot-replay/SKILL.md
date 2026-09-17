---
name: morning-boot-replay
description: >-
  Morning boot MP3 replay until midday (job morning-boot-replay).
---

# morning-boot-replay

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("morning_boot_replay")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
