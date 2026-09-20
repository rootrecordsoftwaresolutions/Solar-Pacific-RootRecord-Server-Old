---
name: official-weather-media
description: >-
  Official NHC/NWS graphics and HLS media (job official-weather-media). Use
  when asked how those assets are fetched or applied to OBS.
---

# official-weather-media

This folder **is** the runtime. Do not invent storm names.

## How it fires

- Scheduler job id `official-weather-media`, every **10 minutes** HST.
- Still runs in night sleep (latest HLS/HWO + graphics).
- Processor: `scripts/official_weather_media.py`. Job: `scripts/job.py` (also `apply_obs_scenes`).
- `apps.core.services.official_weather_media` execs the processor.

Topic index: `weather-kilauea`.
