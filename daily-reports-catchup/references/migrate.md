# Migrate `daily-reports-catchup`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/daily_reports_catchup.py` |
| To | `~/.ollama/skills/daily-reports-catchup/scripts/job.py` |
| Scheduler | `_run("daily_reports_catchup")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
