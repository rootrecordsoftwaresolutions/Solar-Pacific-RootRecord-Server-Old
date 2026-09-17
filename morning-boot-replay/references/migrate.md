# Migrate `morning-boot-replay`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/since_last_fire/morning_boot_replay.py` |
| To | `~/.ollama/skills/morning-boot-replay/scripts/job.py` |
| Scheduler | `_run("morning_boot_replay")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
