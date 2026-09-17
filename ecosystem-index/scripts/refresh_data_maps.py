#!/usr/bin/env python3
"""Schema-only DB inventory + bounded live directory index. No row dumps. No secrets."""
from __future__ import annotations

import os
import sqlite3
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
HERE = Path(__file__).resolve().parent
import sys

if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from rr_home import AVA, ECOFLOW, HOME, MEDIA, OPS, REPORTS, SKILLS  # noqa: E402
DATA = SKILLS / "database" / "store"
STATE = SKILLS / "state" / "store"
LOGS = SKILLS / "logs" / "store"

SKIP_DIR = {
    ".git",
    ".venv",
    "node_modules",
    "vendor",
    "pb",
    "__pycache__",
    "credentials",
    "private",
    "Media.bak-20260915",
    "SteamLibrary",
    "ollama-models",
    ".Trash-1000",
}

DENY_FILE = {".env", ".env.local", "credentials.json", "secrets.env"}

DIR_ROOTS = [
    (AVA, 2, "Ava-Core live tree"),
    (DATA, 3, "database skill store"),
    (STATE, 2, "state skill store"),
    (LOGS, 2, "logs skill store"),
    (ECOFLOW, 2, "EcoFlow store"),
    (REPORTS, 2, "hybrid reports store"),
    (OPS, 2, "core-ops-install store"),
    (MEDIA / "public", 2, "Media public"),
    (Path("/mnt"), 1, "mnt top (not Steam/games)"),
]

DB_ROOTS = [
    DATA,
    ECOFLOW,
    AVA / "apps" / "core",
]


def now_iso() -> str:
    return datetime.now(HST).isoformat(timespec="seconds")


def skip_dir(name: str) -> bool:
    return name in SKIP_DIR or name.startswith(".") and name not in {".cursor"}


def walk_dirs(root: Path, max_depth: int) -> list[str]:
    lines: list[str] = []
    if not root.exists():
        return [f"- missing `{root}`"]

    def rec(cur: Path, depth: int, prefix: str) -> None:
        if depth > max_depth:
            return
        try:
            kids = sorted(cur.iterdir(), key=lambda p: p.name.lower())
        except OSError as e:
            lines.append(f"{prefix}- unreadable `{cur.name}`: {e}")
            return
        dirs = [p for p in kids if p.is_dir() and not skip_dir(p.name)]
        files = [p for p in kids if p.is_file() and p.name not in DENY_FILE]
        nfiles = len(files)
        ndirs = len(dirs)
        if depth == 0:
            lines.append(f"- `{cur}` — {ndirs} dirs, {nfiles} files (this level)")
        for d in dirs:
            try:
                sub = list(d.iterdir())
                sd = sum(1 for x in sub if x.is_dir() and not skip_dir(x.name))
                sf = sum(1 for x in sub if x.is_file())
            except OSError:
                sd, sf = 0, 0
            lines.append(f"{prefix}  - `{d.name}/` — {sd} dirs, {sf} files")
            rec(d, depth + 1, prefix + "  ")

    rec(root, 0, "")
    return lines


def sqlite_schema(path: Path) -> dict:
    out: dict = {"path": str(path), "tables": []}
    try:
        st = path.stat()
        out["bytes"] = st.st_size
        out["mtime"] = datetime.fromtimestamp(st.st_mtime, HST).isoformat(timespec="seconds")
    except OSError as e:
        out["error"] = str(e)
        return out
    if st.st_size > 40_000_000:
        out["tables"] = [{"name": "(skipped table scan)", "columns": ["file > 40MB"], "rows": None}]
        return out
    try:
        uri = path.resolve().as_posix()
        con = sqlite3.connect(f"file:{uri}?mode=ro", uri=True)
    except sqlite3.Error as e:
        out["error"] = str(e)
        return out
    try:
        names = [
            r[0]
            for r in con.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY 1"
            )
        ]
        for name in names[:40]:
            cols = [c[1] for c in con.execute(f"PRAGMA table_info({name})")]
            try:
                n = con.execute(f"SELECT COUNT(*) FROM {name}").fetchone()[0]
            except sqlite3.Error:
                n = None
            out["tables"].append({"name": name, "columns": cols, "rows": n})
    except sqlite3.Error as e:
        out["error"] = str(e)
    finally:
        con.close()
    return out


def find_sqlite() -> list[Path]:
    found: list[Path] = []
    seen: set[Path] = set()
    for root in DB_ROOTS:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".db", ".sqlite", ".sqlite3"}:
                continue
            if any(skip_dir(part) for part in p.parts):
                continue
            rp = p.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            found.append(rp)
            if len(found) >= 40:
                return found
    return found


def write_dirs() -> None:
    dest = SKILLS / "live-directories"
    dest.mkdir(parents=True, exist_ok=True)
    # Do not overwrite topic SKILL.md. Full-machine index is fs-index.
    lines = [
        "# Live directories — canonical trees (bounded)",
        "",
        f"Generated {now_iso()}. Directory names and counts only.",
        "",
        "Full-machine `paths.txt` is `~/.ollama/skills/fs-index/`. This file is a shallow tree of RootRecord folders.",
        "",
        "Skipped: private Media, credentials, git/venv/node_modules, Steam, ollama-models dumps.",
        "",
    ]
    for root, depth, title in DIR_ROOTS:
        lines.append(f"## {title}")
        lines.append("")
        lines.extend(walk_dirs(root, depth))
        lines.append("")
    (dest / "CURRENT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_dbs() -> None:
    dest = SKILLS / "desk-data-reader"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "SKILL.md").write_text(
        """---
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

Do not import `D:\\db backup` or `/mnt/4tb/db backup` as live.

## MySQL

Use `apps.core.services.mysql.status()` and `mysql.query(...)`. Local pool may
be down; RootMC remote is env-gated. **Never** copy host/user/password into a
skill or chat.

## JSONL / JSON live

Prefer growing jsonl (EcoFlow history, host history, night-mode, ecoflow-live)
over a frozen sqlite row. Schema refresh does not replace a live tail read.

## Refresh

`python3 .cursor/skills/ecosystem-index/scripts/refresh-all.py`
""",
        encoding="utf-8",
    )
    lines = [
        "# Desk databases — generated schema only",
        "",
        f"Generated {now_iso()}. No row payloads.",
        "",
    ]
    for path in find_sqlite():
        info = sqlite_schema(path)
        try:
            rel = str(path.relative_to(HOME))
        except ValueError:
            rel = str(path)
        lines.append(f"## `{rel}`")
        lines.append("")
        if info.get("error"):
            lines.append(f"- error: {info['error']}")
            lines.append("")
            continue
        lines.append(f"- size: {info.get('bytes')} bytes")
        lines.append(f"- mtime: {info.get('mtime')}")
        if not info.get("tables"):
            lines.append("- tables: (none)")
        for t in info["tables"]:
            cols = ", ".join(t["columns"][:24])
            rows = t["rows"]
            lines.append(f"- `{t['name']}` rows={rows} cols: {cols}")
        lines.append("")
    lines += [
        "## MySQL",
        "",
        "- Probe via `apps.core.services.mysql.status()` → `local_3306`, `shockbyte`, `live`.",
        "- Do not persist connection settings here.",
        "",
    ]
    (dest / "CURRENT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    write_dbs()
    print("wrote desk-data-reader CURRENT.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
