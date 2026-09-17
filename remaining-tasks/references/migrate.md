# Migrate `remaining-tasks`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/since_last_fire/remaining_tasks.py` |
| To | `~/.ollama/skills/remaining-tasks/scripts/job.py` |
| Scheduler | `_run("remaining_tasks")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
