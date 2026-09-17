# Migrate `late-report-play`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/late_report_play.py` |
| To | `~/.ollama/skills/late-report-play/scripts/job.py` |
| Scheduler | `_run("late_report_play")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
