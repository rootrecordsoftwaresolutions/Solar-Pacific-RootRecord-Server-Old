---
name: state
description: >-
  Live AVA JSON state store (STATE_DIR). Use when asked where cron/ops
  state json lives, feature-toggles, or kilauea-alert.json.
---

# state

This folder **is** the runtime store. Do not dump secrets in chat.

## Store

Live tree: `store/` (`STATE_DIR`). Origin and desks write JSON here.

`.env` stays in Ava-Core. SQLite stays in the `database` skill. Runtime logs stay in the `logs` skill.

## Paths

`scripts/paths.py` exports `STORE`.
