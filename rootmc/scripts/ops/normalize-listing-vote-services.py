#!/usr/bin/env python3
"""Normalize Votifier service names in MySQL rewards_votes + D1 rootmc_listing_votes."""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
WRANGLER_DIR = WORKSPACE / "Web Files" / "rootmc-api"
WRANGLER_DB = "rootmc"

# Mirrors rootmc-listing-sites.ts / ListingSiteCanonical.java
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


def read_db_yaml() -> dict:
    text = DB_YAML.read_text(encoding="utf-8")

    def get(key: str) -> str:
        m = re.search(rf"^\s*{re.escape(key)}:\s*'?([^'\r\n]+)'?\s*$", text, re.M)
        return m.group(1).strip()

    return {
        "host": get("hostname"),
        "port": int(get("port") or 3306),
        "user": get("username"),
        "password": get("password"),
        "database": get("dbname"),
    }


def sql_escape(value: str) -> str:
    return value.replace("'", "''")


def d1_query(sql: str) -> list[dict]:
    sql_one = " ".join(sql.split())
    cmd = (
        f'npx wrangler d1 execute {WRANGLER_DB} --remote --json '
        f'--command "{sql_one.replace(chr(34), chr(92) + chr(34))}"'
    )
    res = subprocess.run(cmd, cwd=WRANGLER_DIR, env=dict(os.environ), capture_output=True, text=True, shell=True)
    if res.returncode != 0:
        raise RuntimeError(res.stderr or res.stdout)
    payload = json.loads(res.stdout)
    return payload[0].get("results") or []


def d1_exec_file(sql: str):
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write(sql)
        sql_path = f.name
    try:
        cmd = f'npx wrangler d1 execute {WRANGLER_DB} --remote --json --file "{sql_path}"'
        res = subprocess.run(cmd, cwd=WRANGLER_DIR, env=dict(os.environ), capture_output=True, text=True, shell=True)
        if res.returncode != 0:
            raise RuntimeError(res.stderr or res.stdout)
    finally:
        try:
            os.remove(sql_path)
        except OSError:
            pass


def normalize_mysql(conn, dry_run: bool) -> int:
    cur = conn.cursor()
    cur.execute("SELECT DISTINCT service FROM root_rewards_votes")
    mapping = {}
    for (svc,) in cur.fetchall():
        canon = canonicalize(str(svc))
        if canon and canon != str(svc).strip().lower():
            mapping[str(svc)] = canon
    if not mapping:
        print("MySQL: no service renames needed.")
        return 0
    print("MySQL renames:", mapping)
    updated = 0
    for old, new in mapping.items():
        if dry_run:
            cur.execute("SELECT COUNT(*) FROM root_rewards_votes WHERE service = %s", (old,))
            updated += int(cur.fetchone()[0])
            continue
        cur.execute("UPDATE root_rewards_votes SET service = %s WHERE service = %s", (new, old))
        updated += cur.rowcount
    if not dry_run:
        conn.commit()
    print(f"MySQL: updated {updated} row(s).")
    return updated


def normalize_d1(dry_run: bool) -> int:
    rows = d1_query("SELECT DISTINCT service FROM rootmc_listing_votes")
    mapping = {}
    for row in rows:
        svc = str(row.get("service") or "")
        canon = canonicalize(svc)
        if canon and canon != svc.strip().lower():
            mapping[svc] = canon
    if not mapping:
        print("D1: no service renames needed.")
        return 0
    print("D1 renames:", mapping)
    if dry_run:
        total = 0
        for old in mapping:
            total += int(d1_query(f"SELECT COUNT(*) as c FROM rootmc_listing_votes WHERE service = '{sql_escape(old)}'")[0]["c"])
        print(f"D1: would touch {total} row(s).")
        return total

    stmts: list[str] = []
    for old, new in mapping.items():
        rows = d1_query(
            f"SELECT minecraft_uuid, service, voted_at, synced_at FROM rootmc_listing_votes "
            f"WHERE service = '{sql_escape(old)}'"
        )
        for r in rows:
            uuid = sql_escape(str(r["minecraft_uuid"]))
            voted = sql_escape(str(r["voted_at"]))
            synced = sql_escape(str(r.get("synced_at") or voted))
            new_esc = sql_escape(new)
            stmts.append(
                f"DELETE FROM rootmc_listing_votes WHERE minecraft_uuid = '{uuid}' "
                f"AND service = '{sql_escape(old)}' AND voted_at = '{voted}'"
            )
            stmts.append(
                f"INSERT INTO rootmc_listing_votes (minecraft_uuid, service, voted_at, synced_at) "
                f"VALUES ('{uuid}', '{new_esc}', '{voted}', '{synced}') "
                f"ON CONFLICT(minecraft_uuid, service, voted_at) DO UPDATE SET synced_at = excluded.synced_at"
            )
    if stmts:
        d1_exec_file(";\n".join(stmts) + ";")
    print(f"D1: migrated {len(stmts) // 2} listing vote row(s).")
    return len(stmts) // 2


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    try:
        normalize_mysql(conn, args.dry_run)
    finally:
        conn.close()
    normalize_d1(args.dry_run)


if __name__ == "__main__":
    main()
