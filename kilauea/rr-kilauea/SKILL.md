---
name: rr-kilauea
description: >-
  Hourly Kīlauea USGS/HVO poll (job rr-kilauea). Use when asked how that job
  runs or where the processor lives. Reports stay in Core Ops Reports/.
---

# rr-kilauea

This folder **is** the runtime. Do not invent alert levels or watts.

## How it fires

- Scheduler job id `rr-kilauea`, every **60 minutes** HST.
- Still runs in night sleep.
- `apps.core.services.kilauea` is a 5-line exec of `scripts/kilauea.py` (get_multiplier for other jobs).
- Public post is gated on disk (`kilauea-publish.json`): HVO notice id + alert level. No notice id does not overwrite the last hash. USGS quake lists do not set alert level or republish. Hourly clip job does not Telegram Kīlauea — only this cron does.

Live markdown lands under Core Ops `Reports/`. State json stays in `state/store/`.

Topic index: `weather-kilauea`.
