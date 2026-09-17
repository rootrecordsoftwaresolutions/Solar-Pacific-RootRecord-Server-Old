#!/usr/bin/env python3
"""Rebalance treasury ledger: drop bogus operator grants and rows for removed towns."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
NORMALIZE_SCRIPT = WORKSPACE / "scripts" / "normalize-treasury-ledger.py"
SYNC_GRANTS_SCRIPT = WORKSPACE / "scripts" / "sync-claim-bonus-grants.py"

# Towns removed after fresh-world reset â€” purge their backfill + grant rows.
REMOVED_TOWNS = ("bestopolis", "Montania", "Aquroya", "Gas_Station", "RD")
OPERATOR_UUID = "3e660994-b16c-4714-bc15-9081aa928729"
BOGUS_GRANT_MIN = 100.0


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


def current_towns(conn) -> set[str]:
    cur = conn.cursor()
    cur.execute("SELECT name FROM TOWNY_TOWNS")
    return {str(r[0]) for r in cur.fetchall()}


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
    print(f"  GRANT total: {cur.fetchone()[0]} G")


def main() -> int:
    cfg = read_db_yaml()
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **cfg)
    try:
        live_towns = current_towns(conn)
        print("Live Towny towns:", sorted(live_towns))

        audit(conn, "BEFORE")

        cur = conn.cursor()
        removed = list(REMOVED_TOWNS)

        # 1) Bogus operator grants (1000 G spam with no reason).
        cur.execute(
            """
            DELETE FROM root_treasury_ledger
            WHERE entry_type = 'GRANT'
              AND amount >= %s
              AND details LIKE %s
            """,
            (BOGUS_GRANT_MIN, f"operator={OPERATOR_UUID};%"),
        )
        bogus_deleted = cur.rowcount
        print(f"\nDeleted bogus operator grants: {bogus_deleted}")

        # 2) Founding grant backfill for towns that no longer exist.
        grant_backfill_deleted = 0
        for town in removed:
            pattern = f"backfill:grant:town:{town};%"
            cur.execute(
                "DELETE FROM root_treasury_ledger WHERE entry_type='GRANT' AND details LIKE %s",
                (pattern,),
            )
            grant_backfill_deleted += cur.rowcount
        print(f"Deleted grant backfill for removed towns: {grant_backfill_deleted}")

        # 3) Towny sink backfill for removed towns (founding, claims, bonus).
        sink_deleted = 0
        for town in removed:
            for pattern in (
                f"backfill:towny:new-town:{town}%",
                f"backfill:towny:claim:{town}:%",
                f"backfill:towny:bonus-block:{town}:%",
            ):
                cur.execute(
                    "DELETE FROM root_treasury_ledger WHERE entry_type='TOWNY_SINK' AND details LIKE %s",
                    (pattern,),
                )
                sink_deleted += cur.rowcount
        print(f"Deleted towny sink backfill for removed towns: {sink_deleted}")

        conn.commit()
        audit(conn, "AFTER MySQL cleanup")
    finally:
        conn.close()

    print("\nSyncing claim/bonus grants and resyncing D1 ...")
    result = subprocess.run([sys.executable, str(SYNC_GRANTS_SCRIPT)], cwd=WORKSPACE)
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
