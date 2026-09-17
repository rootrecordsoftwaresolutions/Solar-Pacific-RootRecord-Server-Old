# Migrate `hourly-clip-reports`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/since_last_fire/hourly_clip_reports.py` |
| To | `~/.ollama/skills/hourly-clip-reports/scripts/job.py` |
| Scheduler | `_run("hourly_clip_reports")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
