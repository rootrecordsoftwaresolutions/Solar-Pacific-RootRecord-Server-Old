# Migrate `midday-report`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/midday_report.py` |
| To | `~/.ollama/skills/midday-report/scripts/job.py` |
| Scheduler | `_run("midday_report")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
