# Migrate `late-report`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/late_report.py` |
| To | `~/.ollama/skills/late-report/scripts/job.py` |
| Scheduler | `_run("late_report")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
