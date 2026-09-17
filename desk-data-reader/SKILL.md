---
name: desk-data-reader
description: >-
  How to read live Ava SQLite and MySQL. Live sqlite lives in the `database`
  skill `store/`. Use when asked for identities store, EcoFlow sqlite, quakes,
  governance, people, guests, or RootMC MySQL. Schema in CURRENT.md; do not dump rows.
---

# Desk data reader

Query live files. Do not dump rows or secrets in chat. Prefer `apps.core.services.db_facts` for spoken EcoFlow/host lines.

## SQLite

Read-only URI, same as db_facts:

```python
from apps.core.services.db_facts import open_sqlite_ro
con = open_sqlite_ro(path)
# PRAGMA table_info / COUNT / last row — then con.close()
```

Known stores (paths on CURRENT.md after refresh):

- EcoFlow `ecoflow-10s.db` under Core Ops Ecoflow
- `~/.ollama/skills/database/store/db/identities.sqlite` (and people, guests, governance, api-ledger)
- weather `quakes.db` / `weather.db` / `system.db` under `store/db` when present

Never `SELECT *` from people or identities into chat. Count or a named lookup
only if the operator asked. Never print emails/UUIDs unless they asked.

Do not import `D:\db backup` or `/mnt/4tb/db backup` as live.

## MySQL

Use `apps.core.services.mysql.status()` and `mysql.query(...)`. Local pool may
be down; RootMC remote is env-gated. **Never** copy host/user/password into a
skill or chat.

## JSONL / JSON live

Prefer growing jsonl (EcoFlow history, host history, night-mode, ecoflow-live)
over a frozen sqlite row. Schema refresh does not replace a live tail read.

## Refresh

`python3 .cursor/skills/ecosystem-index/scripts/refresh-all.py`
