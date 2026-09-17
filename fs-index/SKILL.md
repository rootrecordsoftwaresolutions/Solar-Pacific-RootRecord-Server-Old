---
name: fs-index
description: >-
  Incremental full-machine path index (paths.txt). Use when locating anything
  on this OmniBook, or when asked how fs-index / live directory indexing runs.
  Grep paths.txt; do not cat secrets.
---

# fs-index

This folder **is** the runtime. Do not look in Ava-Core crons for a second copy.

## How it fires

- Scheduler job id `fs-index`, every **15 minutes** HST.
- **Runs through night sleep** (Ava jobs otherwise skip).
- Origin must be up (`127.0.0.1:8787`) for the job. Manual:

```bash
python3 ~/.ollama/skills/fs-index/scripts/incremental_fs_index.py
```

Directory mtime unchanged → keep existing lines for that prefix.
mtime changed → re-scandir that folder only.

## Live outputs (here, not Media)

| Path | Role |
| --- | --- |
| `paths.txt` | One path per line (`path`, `kind`, optional symlink target) |
| `CURRENT.md` | Last run stats |
| `state/dir-mtimes.json` | Directory mtimes for incremental reuse |

Do not put `.env`, sqlite dumps, or quota JSON here. Index may list `.env` **names**. Never print contents.

## Stubs (listed, not opened)

`/proc` `/sys` `/dev` `/run` `/snap` `/boot`, `.git/objects`, `node_modules`, `.venv`, Docker overlay, SteamLibrary, `android-sdk` `data`/`lib`/`.temp`. Symlinks recorded, not followed (Media stays one tree).

Topic index: `live-directories`.
