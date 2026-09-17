# Migrate `hurricane-tracker`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core services/hurricane_tracker.py + since_last_fire/hurricane_tracker.py |
| To | `~/.ollama/skills/hurricane-tracker/scripts/` |
| Scheduler | legacy `_run("hurricane_tracker")` → job.py; live fetch is hurricane-fetch |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
