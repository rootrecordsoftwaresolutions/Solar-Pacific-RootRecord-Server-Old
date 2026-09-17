# Migrate `midday-report-play`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/midday_report_play.py` |
| To | `~/.ollama/skills/midday-report-play/scripts/job.py` |
| Scheduler | `_run("midday_report_play")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
