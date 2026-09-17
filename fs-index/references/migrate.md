# Migrate `fs-index`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/apps/core/crons/always_on/fs_index.py` (subprocess shim) and `~/.ollama/skills/live-directories/scripts/incremental_fs_index.py` |
| To | `~/.ollama/skills/fs-index/scripts/incremental_fs_index.py` |
| Scheduler | job id `fs-index` → `Scheduler._run_fs_index()` execs the skill script |
| Night sleep | Job still runs |
| Deleted | Ava-Core `always_on/fs_index.py` |
| Outputs | `paths.txt`, `CURRENT.md`, `state/` stay in this skill |
| Topic | `live-directories` is the grouping only |

Also moved: `scripts/print_directory.py`. Ava-Core `tools/print_directory.py` is a shim.

Do not restore the Ava-Core cron module.
