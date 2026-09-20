# Migrate `hurricane-obs`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core crons/on_time/hurricane_obs.py |
| To | `~/.ollama/skills/hurricane-obs/scripts/` |
| Scheduler | `_run("hurricane_obs")` |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
