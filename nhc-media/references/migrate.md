# Migrate `nhc-media`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core services/nhc_media.py + no-op cron |
| To | `~/.ollama/skills/nhc-media/scripts/` |
| Scheduler | `_run("nhc_media")` if a leftover id exists |
| Deleted | Ava-Core cron wrapper where it existed |

Do not restore the old runner body.
