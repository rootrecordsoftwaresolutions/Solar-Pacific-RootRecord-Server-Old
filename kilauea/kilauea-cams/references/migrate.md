# Migrate `kilauea-cams`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core services/kilauea_cams.py + always_on/kilauea_cams.py |
| Scheduler | OBS-gated `_run("kilauea_cams")` → job.py |
| Deleted | Ava-Core cron wrapper; service/council module is shim |

Do not restore the old runner body.
