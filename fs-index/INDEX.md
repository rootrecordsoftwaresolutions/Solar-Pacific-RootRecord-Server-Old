# Desk — fs-index

Function desk. Runtime is `scripts/incremental_fs_index.py`.
Do not invent watts, SOC, or player counts. Do not open `.env`.

| In this desk | Role |
| --- | --- |
| `scripts/incremental_fs_index.py` | Runner |
| `paths.txt` | Live path index |
| `CURRENT.md` | Last run stats |
| `state/dir-mtimes.json` | Incremental mtimes |
| `desk/src/apps/core/scheduler.py` | Job registration (`fs-index`, 15 min) |
