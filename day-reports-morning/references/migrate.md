# Migrate `day-reports-morning`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/day_reports_morning.py` |
| To | `~/.ollama/skills/day-reports-morning/scripts/job.py` |
| Scheduler | `_run("day_reports_morning")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
