---
name: database
description: >-
  Live AVA sqlite store (DATA_DIR). Use when asked where databases
  live, DATA_DIR, or local sqlite files.
---

# database

This folder **is** the runtime store. Do not dump rows or secrets in chat.

## Store

Live tree: `store/`. Origin `DATA_DIR` and launch `AVA_DATA_DIR` point here.

Keep here: `db/` (sqlite including `quakes.db`, `weather.db`, `system.db`), plus other live json/sqlite desks already in this store.

Moved out of this store:

- review packs → `code-review/store/`
- Slack archives → `slack/store/`
- training jsonl → `reply-feedback/store/`
- JSON state → `state/store/` (`STATE_DIR`)
- runtime logs → `logs/store/` (`RUNTIME_LOGS`)

Do not recreate `store/state` or `store/logs` here.

Ava-Core has no `data/` tree. `.env` stays in Ava-Core. EcoFlow quota JSON lives in `ecoflow-ble-poller/store/quota`. Media stays `$HOME/Media`.

## Paths

`scripts/paths.py` exports `STORE`.
