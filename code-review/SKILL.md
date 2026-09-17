---
name: code-review
description: >-
  Write a review pack. Never patches source (job code-review).
---

# code-review

This folder **is** the runtime.

## How it fires

- Scheduler `_run("code_review")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Packs land in `store/` (`CURRENT.md`, dated files, `DROP-INTO-CURSOR.md`).

Topic index: `boot-idle-origin`.
