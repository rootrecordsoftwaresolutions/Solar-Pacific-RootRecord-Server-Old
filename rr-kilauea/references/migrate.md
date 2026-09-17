# Migrate `rr-kilauea`

Status: **moved**.

| | |
| --- | --- |
| From | `RootRecord-Core-Ops/Kilauea/kilauea.py` + `Ava-Core/apps/core/crons/since_last_fire/kilauea.py` |
| To | `~/.ollama/skills/rr-kilauea/scripts/kilauea.py` |
| Scheduler | job id `rr-kilauea` → `_run("kilauea")` loads the skill file |
| Shim | `apps/core/services/kilauea.py` execs the skill (get_multiplier) |
| Night sleep | Skips this job |
| Deleted | Core Ops processor + Ava-Core cron wrapper |

Do not restore those two files.
