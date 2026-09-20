# Migrate `ecoflow-river-car`

Status: **moved**. Scheduler job `drive-automation` registered; copy still stub.

| | |
| --- | --- |
| Runtime | `~/.ollama/skills/ecoflow-river-car/scripts/drive_automation.py` |
| Switch | `scripts/river_car_dc.py` (`mpptCar` only) |
| State | Core Ops `Ecoflow/state/drive-automation.json`, `river-car-dc.json` |
| Ops | `GET`/`POST /api/ops/drive-automation` |
| Shim | Ava-Core `apps/core/services/drive_automation.py` |
| Not built | rsync/unmount bodies inside `run_copy_jobs` |

Do not restore drive power onto Delta AC or River AC.
