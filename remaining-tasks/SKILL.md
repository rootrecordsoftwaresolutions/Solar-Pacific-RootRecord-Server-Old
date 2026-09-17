---
name: remaining-tasks
description: >-
  Remaining-tasks spoken desk (job remaining-tasks).
---

# remaining-tasks

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("remaining_tasks")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Catalog/remaining board is `scripts/day_board.py`. Spoken cron is `scripts/job.py`.

Topic index: `reports-voice`.
