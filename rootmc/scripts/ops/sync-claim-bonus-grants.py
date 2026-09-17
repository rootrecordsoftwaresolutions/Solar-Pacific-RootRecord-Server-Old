#!/usr/bin/env python3
"""DEPRECATED â€” do not run. Use scripts/reconcile-treasury-ledger.py instead.

This script inserted ledger-only GRANT rows that never touched the vault.
"""
import sys

print("ERROR: sync-claim-bonus-grants.py is deprecated. Use reconcile-treasury-ledger.py.", file=sys.stderr)
sys.exit(1)


import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
NORMALIZE_SCRIPT = WORKSPACE / "scripts" / "normalize-treasury-ledger.py"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
GRANT_PREFIX = "normalize:grant:sink:"
CLAIM_FLAT_FEE = 12


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


def mayor_uuid(conn, town: str) -> str | None:
    cur = conn.cursor()
    cur.execute("SELECT mayor FROM TOWNY_TOWNS WHERE name = %s LIMIT 1", (town,))
    row = cur.fetchone()
    return str(row[0]) if row and row[0] else None


def parse_town_from_sink(details: str) -> str | None:
    if details.startswith("backfill:towny:claim:"):
        return details.split(":")[3]
    if details.startswith("backfill:towny:bonus-block:"):
        return details.split(":")[3]
    return None


def is_claim_or_bonus_sink(details: str) -> bool:
    d = details.lower()
    return (
        d.startswith("towny:claim")
        or d.startswith("backfill:towny:claim:")
        or d.startswith("backfill:towny:bonus-block:")
        or d == "towny:other"
    )


def grant_time_for_sink(created_at) -> str:
    if isinstance(created_at, dt.datetime):
        when = created_at - dt.timedelta(seconds=30)
    else:
        when = dt.datetime.fromisoformat(str(created_at).replace(" ", "T")) - dt.timedelta(seconds=30)
    return when.strftime("%Y-%m-%d %H:%M:%S")


def audit(conn, label: str) -> None:
    cur = conn.cursor()
    print(f"\n=== {label} ===")
    cur.execute(
        """
        SELECT entry_type, ROUND(SUM(amount), 2)
        FROM root_treasury_ledger
        GROUP BY entry_type
        ORDER BY entry_type
        """
    )
    for row in cur.fetchall():
        print(f"  {row[0]}: {row[1]} G")

    cur.execute(
        """
        SELECT ROUND(SUM(CASE
          WHEN entry_type IN ('GRANT','DIVIDEND','LOAN_DISBURSE','VOTE') THEN -amount
          ELSE amount END), 2)
        FROM root_treasury_ledger
        """
    )
    print(f"  LEDGER NET: {cur.fetchone()[0]} G")

    cur.execute(
        """
        SELECT ROUND(COALESCE(SUM(amount),0),2)
        FROM root_treasury_ledger
        WHERE entry_type='GRANT'
        """
    )
    grant_total = cur.fetchone()[0]
    cur.execute(
        """
        SELECT ROUND(COALESCE(SUM(amount),0),2)
        FROM root_treasury_ledger
        WHERE entry_type='TOWNY_SINK'
          AND (
            details LIKE 'towny:claim%%'
            OR details LIKE 'backfill:towny:claim%%'
            OR details LIKE 'backfill:towny:bonus-block%%'
            OR details = 'towny:other'
          )
        """
    )
    sink_total = cur.fetchone()[0]
    print(f"  GRANT total: {grant_total} G | claim/bonus sinks: {sink_total} G")


def main() -> int:
    cfg = read_db_yaml()
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **cfg)
    try:
        audit(conn, "BEFORE")

        cur = conn.cursor()

        # Drop pre-reset backfill rows (fresh-map ledger should not double-count archive cycle).
        cur.execute("DELETE FROM root_treasury_ledger WHERE details LIKE 'backfill:%'")
        print(f"\nDeleted backfill rows: {cur.rowcount}")

        # Remove bogus operator grants (anything not tier-based and not our normalize rows).
        cur.execute(
            """
            DELETE FROM root_treasury_ledger
            WHERE entry_type = 'GRANT'
              AND details LIKE 'operator=%%'
              AND details NOT LIKE '%%tier=%%'
              AND amount > %s
            """,
            (CLAIM_FLAT_FEE,),
        )
        print(f"Deleted oversized operator grants: {cur.rowcount}")

        # Rebuild normalize grants from claim/bonus sinks.
        cur.execute("DELETE FROM root_treasury_ledger WHERE entry_type='GRANT' AND details LIKE %s", (GRANT_PREFIX + "%",))
        print(f"Deleted prior normalize grants: {cur.rowcount}")

        cur.execute(
            """
            SELECT id, amount, details, created_at
            FROM root_treasury_ledger
            WHERE entry_type = 'TOWNY_SINK'
              AND (
                details LIKE 'towny:claim%%'
                OR details LIKE 'backfill:towny:claim%%'
                OR details LIKE 'backfill:towny:bonus-block%%'
                OR details = 'towny:other'
              )
            ORDER BY id
            """
        )
        sinks = cur.fetchall()
        default_uuid = "d24c48ed-a6af-4827-bd37-c868be39a50d"  # ZuppaFredda â€” primary claim grantee
        inserted = 0
        for sink_id, amount, details, created_at in sinks:
            amt = round(float(amount), 2)
            if amt <= 0:
                continue
            town = parse_town_from_sink(str(details))
            to_uuid = mayor_uuid(conn, town) if town else default_uuid
            grant_details = f"{GRANT_PREFIX}{sink_id};{details}"
            cur.execute(
                """
                INSERT INTO root_treasury_ledger
                  (entry_type, amount, from_uuid, to_uuid, details, created_at)
                VALUES ('GRANT', %s, %s, %s, %s, %s)
                """,
                (amt, TOWNY_SERVER_UUID, to_uuid, grant_details, grant_time_for_sink(created_at)),
            )
            inserted += 1
        print(f"Inserted normalize grants for claim/bonus sinks: {inserted}")

        conn.commit()
        audit(conn, "AFTER")
    finally:
        conn.close()

    print("\nResyncing D1 from MySQL ...")
    result = subprocess.run([sys.executable, str(NORMALIZE_SCRIPT)], cwd=WORKSPACE)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
