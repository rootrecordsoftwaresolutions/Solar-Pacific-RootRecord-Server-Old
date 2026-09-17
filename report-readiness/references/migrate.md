# Migrate `report-readiness`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/report_readiness.py` |
| To | `~/.ollama/skills/report-readiness/scripts/job.py` |
| Scheduler | `_run("report_readiness")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
