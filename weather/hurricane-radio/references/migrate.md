# Migrate `hurricane-radio`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core crons/on_time/hurricane_radio.py |
| To | `~/.ollama/skills/hurricane-radio/scripts/` |
| Scheduler | `_run("hurricane_radio")` |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
