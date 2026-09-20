# Migrate `official-weather-media`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/apps/core/services/official_weather_media.py` + cron wrapper |
| To | `~/.ollama/skills/official-weather-media/scripts/` |
| Scheduler | `_run("official_weather_media")` → `scripts/job.py` |
| Shim | `apps/core/services/official_weather_media.py` execs the processor |
| Deleted | Ava-Core cron wrapper; service is shim only |

Also moved: `scripts/weatherGifLoop.py` (hourly weather GIF stitcher). Workstation copy is a shim. GIFs stay in Media.

Do not restore the old service body.
