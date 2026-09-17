# Migrate `council-quake`

Status: **moved**.

| | |
| --- | --- |
| From | Ava-Core apps/council/quake_watch.py + always_on/council_quake.py |
| Scheduler | `_run("council_quake")` every 2 min; not skipped in night sleep |
| Deleted | Ava-Core cron wrapper; service/council module is shim |

Do not restore the old runner body.
