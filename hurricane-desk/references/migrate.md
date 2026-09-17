# Migrate `hurricane-desk`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core services/hurricane_desk.py + crons/on_time/hurricane_desk.py |
| To | `~/.ollama/skills/hurricane-desk/scripts/` |
| Scheduler | job ids hurricane-desk / hurricane-desk-evening → `_run("hurricane_desk")` |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
