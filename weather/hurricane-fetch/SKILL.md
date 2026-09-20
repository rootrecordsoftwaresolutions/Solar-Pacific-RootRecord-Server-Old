---
name: hurricane-fetch
description: >-
  Fetch tropical boards (NHC/RAMMB/JTWC). Job hurricane-fetch.
---

# hurricane-fetch

This folder **is** the runtime. Do not invent storm names.

## How it fires

- `scripts/job.py` calls `refresh_storms` via hurricane-tracker shim.
**Skipped in night sleep**.

Topic index: `weather-kilauea`.
