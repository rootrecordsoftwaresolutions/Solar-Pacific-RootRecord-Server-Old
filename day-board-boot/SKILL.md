---
name: day-board-boot
description: >-
  Origin start: prelims then morning slots if before noon.
---

# day-board-boot

This folder **is** the runtime.

## How it fires

- Scheduler `_run("day_board_boot")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `boot-idle-origin`.
