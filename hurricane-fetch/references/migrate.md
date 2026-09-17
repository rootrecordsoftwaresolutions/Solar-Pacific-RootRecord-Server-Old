# Migrate `hurricane-fetch`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core crons/on_time/hurricane_fetch.py |
| To | `~/.ollama/skills/hurricane-fetch/scripts/` |
| Scheduler | `_run("hurricane_fetch")` |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
