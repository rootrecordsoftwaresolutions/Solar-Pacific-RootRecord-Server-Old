# Migrate `cursor-fallback`

Status: **moved**.

| | |
| --- | --- |
| From | `apps/core/crons/on_time/cursor_fallback.py` |
| To | `~/.ollama/skills/cursor-fallback/scripts/job.py` |
| Scheduler | `_run("cursor_fallback")` |
| Shim | Ava-Core cron execs the skill |

Do not restore the old body.
