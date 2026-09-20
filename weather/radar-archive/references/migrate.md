# Migrate `radar-archive`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/apps/core/services/radar_archive.py` + cron wrapper |
| To | `~/.ollama/skills/radar-archive/scripts/` |
| Scheduler | `_run("radar_archive")` → `scripts/job.py` |
| Shim | `apps/core/services/radar_archive.py` execs the processor |
| Deleted | Ava-Core cron wrapper; service is shim only |

Do not restore the old service body.
