#!/usr/bin/env python3
"""Backfill Jul 1 2026 vote payouts missing from root_treasury_ledger (pre-VOTE-ledger channel).

Ledger-only rows â€” vault already debited when rewards were paid. Syncs to D1.
"""
from __future__ import annotations

import argparse
import datetime as dt
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

TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
SERVER_ID = "rootmc"
WRANGLER_DB = "rootmc"
BACKFILL_PREFIX = "backfill:vote:jul1-pre-ledger:"
JUL1_START = "2026-07-01 00:00:00"
JUL1_END = "2026-07-02 00:00:00"


def read_db_yaml() -> dict:
    text = DB_YAML.read_text(encoding="utf-8")

    def get(key: str) -> str:
        m = re.search(rf"^\s*{re.escape(key)}:\s*'?([^'\r\n]+)'?\s*$", text, re.M)
        if not m:
            raise RuntimeError(f"Missing {key} in {DB_YAML}")
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


def voted_at_iso(voted_at) -> str:
    if hasattr(voted_at, "strftime"):
        return voted_at.strftime("%Y-%m-%dT%H:%M:%S.000Z")
    return str(voted_at).replace(" ", "T") + ".000Z" if "T" not in str(voted_at) else str(voted_at)


def d1_exec_file(sql: str):
    env = dict(os.environ)
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write(sql)
        sql_path = f.name
    try:
        cmd = f'npx wrangler d1 execute {WRANGLER_DB} --remote --json --file "{sql_path}"'
        res = subprocess.run(cmd, cwd=WRANGLER_DIR, env=env, capture_output=True, text=True, shell=True)
        if res.returncode != 0:
            raise RuntimeError(res.stderr or res.stdout)
    finally:
        try:
            os.remove(sql_path)
        except OSError:
            pass


def build_entries(conn) -> list[tuple[float, str, str, str, str, int]]:
    cur = conn.cursor()
    cur.execute(
        """
        SELECT v.id, v.uuid, v.service, v.voted_at, v.gold_earned
        FROM root_rewards_votes v
        LEFT JOIN root_treasury_ledger l
          ON l.entry_type = 'VOTE'
         AND l.to_uuid = v.uuid
         AND l.amount = v.gold_earned
         AND ABS(TIMESTAMPDIFF(SECOND, l.created_at, v.voted_at)) < 180
        WHERE v.gold_earned > 0
          AND v.voted_at >= %s AND v.voted_at < %s
          AND l.id IS NULL
        ORDER BY v.voted_at ASC, v.id ASC
        """,
        (JUL1_START, JUL1_END),
    )
    entries: list[tuple[float, str, str, str, str, int]] = []
    for vote_id, player_uuid, service, voted_at, gold in cur.fetchall():
        amt = round(float(gold), 2)
        if amt <= 0:
            continue
        svc = str(service or "default")
        details = f"{BACKFILL_PREFIX}service={svc};rewards_vote_id={int(vote_id)}"
        entries.append(
            (
                amt,
                TOWNY_SERVER_UUID,
                str(player_uuid),
                voted_at_iso(voted_at),
                details,
                int(vote_id),
            )
        )
    return entries


def backfill_exists(conn) -> bool:
    cur = conn.cursor()
    cur.execute(
        "SELECT COUNT(*) FROM root_treasury_ledger WHERE details LIKE %s LIMIT 1",
        (BACKFILL_PREFIX + "%",),
    )
    return int(cur.fetchone()[0]) > 0


def insert_mysql(conn, entries) -> list[tuple[int, float, str, str, str, str]]:
    cur = conn.cursor()
    inserted = []
    for amount, from_uuid, to_uuid, created_at, details, _vote_id in entries:
        cur.execute(
            "INSERT INTO root_treasury_ledger (entry_type, amount, from_uuid, to_uuid, details, created_at) "
            "VALUES ('VOTE', %s, %s, %s, %s, %s)",
            (amount, from_uuid, to_uuid, details, created_at.replace("T", " ").replace("Z", "")),
        )
        mysql_id = int(cur.lastrowid)
        inserted.append((mysql_id, amount, from_uuid, to_uuid, details, created_at))
    conn.commit()
    return inserted


def sync_rows_to_d1(rows):
    synced = dt.datetime.now(dt.UTC).isoformat().replace("+00:00", "Z")
    batch: list[str] = []
    for mysql_id, amount, from_uuid, to_uuid, details, created_at in rows:
        batch.append(
            "INSERT INTO rootmc_treasury_ledger "
            "(server_id, mysql_id, entry_type, amount, from_uuid, to_uuid, details, created_at, synced_at) "
            f"VALUES ('{sql_escape(SERVER_ID)}', {mysql_id}, 'VOTE', {round(amount, 2)}, "
            f"'{sql_escape(from_uuid)}', '{sql_escape(to_uuid)}', '{sql_escape(details[:512])}', "
            f"'{sql_escape(created_at)}', '{sql_escape(synced)}') "
            "ON CONFLICT(server_id, mysql_id) DO UPDATE SET "
            "entry_type = excluded.entry_type, amount = excluded.amount, "
            "from_uuid = excluded.from_uuid, to_uuid = excluded.to_uuid, "
            "details = excluded.details, created_at = excluded.created_at, synced_at = excluded.synced_at"
        )
    if batch:
        d1_exec_file(";\n".join(batch) + ";")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    try:
        entries = build_entries(conn)
        total = round(sum(e[0] for e in entries), 2)
        print(f"Planned {len(entries)} Jul 1 VOTE ledger rows, total {total} G")

        if not entries:
            print("Nothing to backfill.")
            return

        if backfill_exists(conn):
            if not args.force:
                print("Jul 1 vote backfill already exists. Use --force to replace.")
                return
            if not args.dry_run:
                cur = conn.cursor()
                cur.execute("DELETE FROM root_treasury_ledger WHERE details LIKE %s", (BACKFILL_PREFIX + "%",))
                conn.commit()
                print(f"Removed prior rows: {cur.rowcount}")

        if args.dry_run:
            for e in entries:
                print(" ", e)
            return

        inserted = insert_mysql(conn, entries)
        sync_rows_to_d1(inserted)
        print(f"Jul 1 vote ledger backfill complete ({len(inserted)} rows, {total} G).")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
