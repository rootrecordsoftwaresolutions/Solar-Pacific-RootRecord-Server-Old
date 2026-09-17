# Migrate `nws-hawaii`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/apps/core/services/nws_hawaii.py` + `crons/since_last_fire/nws_hawaii.py` |
| To | `~/.ollama/skills/nws-hawaii/scripts/nws_hawaii.py` |
| Scheduler | job id `nws-hawaii-counties` → `_run("nws_hawaii")` |
| Shim | `apps/core/services/nws_hawaii.py` execs the skill |
| Night sleep | Skips this job |
| Deleted | Ava-Core cron wrapper; service is shim only |

Do not restore the old service body.
