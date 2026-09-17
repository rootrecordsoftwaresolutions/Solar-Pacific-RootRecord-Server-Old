# Migrate `day-reports-midday`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/day_reports_midday.py` |
| To | `~/.ollama/skills/day-reports-midday/scripts/job.py` |
| Scheduler | `_run("day_reports_midday")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
