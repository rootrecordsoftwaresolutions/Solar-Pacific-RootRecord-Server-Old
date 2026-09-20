---
name: cursor-fallback
description: >-
  Drain one Cursor fallback job (job cursor-fallback).
---

# cursor-fallback

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("cursor_fallback")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Queue body is `scripts/cursor_fallback.py`. Cron drain is `scripts/job.py`.

Topic index: `reports-voice`.
