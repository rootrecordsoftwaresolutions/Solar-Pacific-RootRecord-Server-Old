#!/usr/bin/env python3
"""Re-sync rootmc_listing_votes in D1 from MySQL (canonical service ids)."""
from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
WRANGLER_DIR = WORKSPACE / "Web Files" / "rootmc-api"

CANONICAL_RULES: list[tuple[str, re.Pattern]] = [
    ("minecraft-mp", re.compile(r"minecraft-?mp", re.I)),
    ("minecraftservers.org", re.compile(r"minecraftservers\.org", re.I)),
    ("minecraft-server-list", re.compile(r"mcsl|minecraft[- ]?server[- ]?list", re.I)),
    ("minecraftlist.org", re.compile(r"^(minecraftserverslist|minecraftlist\.org)$", re.I)),
    ("minecraft.buzz", re.compile(r"minecraft\.buzz", re.I)),
    ("topminecraftservers", re.compile(r"topminecraftservers", re.I)),
    ("minerank", re.compile(r"minerank", re.I)),
    ("planetminecraft", re.compile(r"planet\s*minecraft|planetminecraft", re.I)),
]


def canonicalize(raw: str) -> str | None:
    s = (raw or "").strip().lower()
    if not s or s == "default":
        return None
    for cid, pat in CANONICAL_RULES:
        if pat.search(s):
            return cid
    return s


def read_db():
    text = DB_YAML.read_text(encoding="utf-8")

    def get(k):
        m = re.search(rf"^\s*{re.escape(k)}:\s*'?([^\r\n']+)'?\s*$", text, re.M)
        return m.group(1).strip()

    return dict(
        host=get("hostname"),
        port=int(get("port") or 3306),
        user=get("username"),
        password=get("password"),
        database=get("dbname"),
    )


def sql_escape(v: str) -> str:
    return v.replace("'", "''")


def main():
    synced = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    conn = pymysql.connect(charset="utf8mb4", **read_db())
    cur = conn.cursor()
    cur.execute("SELECT uuid, service, voted_at FROM root_rewards_votes ORDER BY voted_at")
    rows = cur.fetchall()
    conn.close()

    stmts: list[str] = []
    for uuid, service, voted_at in rows:
        canon = canonicalize(str(service))
        if not canon:
            continue
        voted_iso = voted_at.strftime("%Y-%m-%dT%H:%M:%S.000Z") if hasattr(voted_at, "strftime") else str(voted_at)
        stmts.append(
            "INSERT INTO rootmc_listing_votes (minecraft_uuid, service, voted_at, synced_at) "
            f"VALUES ('{sql_escape(str(uuid).lower())}', '{sql_escape(canon)}', '{sql_escape(voted_iso)}', '{sql_escape(synced)}') "
            "ON CONFLICT(minecraft_uuid, service, voted_at) DO UPDATE SET synced_at = excluded.synced_at"
        )

    out_path = WORKSPACE / "scripts" / "_d1-resync-listing-votes.sql"
    out_path.write_text(";\n".join(stmts) + ";", encoding="utf-8")
    print(f"Wrote {len(stmts)} statements to {out_path}")

    cmd = f'npx wrangler d1 execute rootmc --remote --file "{out_path}"'
    res = subprocess.run(
        cmd,
        cwd=WRANGLER_DIR,
        env=dict(os.environ),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        shell=True,
    )
    if res.returncode != 0:
        raise RuntimeError(res.stderr or res.stdout or "wrangler failed")
    print(res.stdout[-500:] if res.stdout else "Done.")


if __name__ == "__main__":
    main()
