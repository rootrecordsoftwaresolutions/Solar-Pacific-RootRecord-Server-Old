# Migrate `rr-noaa`

Status: **moved**.

| | |
| --- | --- |
| From | `RootRecord-Core-Ops/Weather/weather.py` + `Ava-Core/apps/core/crons/since_last_fire/noaa.py` |
| To | `~/.ollama/skills/rr-noaa/scripts/weather.py` |
| Scheduler | job id `rr-noaa` → `_run("noaa")` loads the skill file |
| Shim | `apps/core/services.weather` execs the skill |
| Night sleep | Skips this job |
| Deleted | Core Ops processor body + Ava-Core cron wrapper |

Do not restore those two files.
