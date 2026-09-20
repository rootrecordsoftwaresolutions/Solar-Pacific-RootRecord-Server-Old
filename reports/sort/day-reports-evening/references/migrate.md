# Migrate `day-reports-evening`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/day_reports_evening.py` |
| To | `~/.ollama/skills/day-reports-evening/scripts/job.py` |
| Scheduler | `_run("day_reports_evening")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
