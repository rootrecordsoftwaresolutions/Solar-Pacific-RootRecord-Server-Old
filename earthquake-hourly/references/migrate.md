# Migrate `earthquake-hourly`

Status: **moved**.

| | |
| --- | --- |
| From | `RootRecord-Core-Ops/Earthquakes/earthquake_hourly.py` + Ava-Core cron |
| To | `~/.ollama/skills/earthquake-hourly/scripts/earthquake_hourly.py` |
| Scheduler | `_run("earthquake_hourly")`; `_eq_poll_m2` calls skill `run(reason="poll")` |
| Shim | `apps/core/services/earthquake_hourly.py` and `earthquake_hourly_processor.py` |
| Night sleep | Skips both jobs |
| Deleted | Core Ops processor body + Ava-Core cron wrapper |

Do not restore those files.
