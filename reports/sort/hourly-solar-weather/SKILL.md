---
name: hourly-solar-weather
description: >-
  Hourly solar + weather combined report (job hourly-solar-weather). Host sample lives here.
---

# hourly-solar-weather

This folder **is** the runtime.

## How it fires

- Scheduler `_run("solar_weather")` loads `scripts/job.py` when this is a scheduled job.
- Ava-Core cron file is a 5-line exec shim.
- Night sleep skips Ava scheduler jobs except where already exempt.

Topic index: `ecoflow-automations`.
