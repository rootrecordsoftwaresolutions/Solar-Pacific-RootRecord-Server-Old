# Migrate `hourly-chime`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/since_last_fire/hourly_chime.py` |
| To | `~/.ollama/skills/hourly-chime/scripts/job.py` |
| Scheduler | `_run("hourly_chime")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
