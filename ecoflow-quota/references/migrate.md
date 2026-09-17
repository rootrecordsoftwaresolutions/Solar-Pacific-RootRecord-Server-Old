# Migrate `ecoflow-quota`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/apps/core/crons/always_on/ecoflow_quota.py` |
| To | `~/.ollama/skills/ecoflow-quota/scripts/ecoflow_quota.py` |
| Scheduler | job id `ecoflow-quota` → `_run("ecoflow_quota")` loads the skill file |
| Night sleep | Skips this job |
| Deleted | Ava-Core `always_on/ecoflow_quota.py` |
| Not moved | `quota/{serial}.json`, history jsonl, BLE poller, gate processor |

Do not restore the Ava-Core cron module.
Processor `ecoflow_public.py` from `apps/core/services/ecoflow_public.py`.

