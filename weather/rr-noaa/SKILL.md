---
name: rr-noaa
description: >-
  Hourly NWS forecast/alerts processor (job rr-noaa). Use when asked how that
  job runs. Reports stay in Core Ops Reports/.
---

# rr-noaa

This folder **is** the runtime. Do not invent alert levels or watts.

## How it fires

- Scheduler job id `rr-noaa`, every **60 minutes** HST.
- Still runs in night sleep.
- `apps.core.services.weather` is a 5-line exec of `scripts/weather.py`.

Live markdown lands under Core Ops `Reports/`.

Topic index: `weather-kilauea`.
