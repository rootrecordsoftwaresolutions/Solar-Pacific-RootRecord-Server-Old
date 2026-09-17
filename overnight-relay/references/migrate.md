# Migrate `overnight-relay`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/overnight.py` |
| To | `~/.ollama/skills/overnight-relay/scripts/job.py` |
| Scheduler | `_run("overnight")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
