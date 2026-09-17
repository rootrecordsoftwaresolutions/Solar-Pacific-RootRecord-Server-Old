#!/usr/bin/env python3
"""One-time Server Reserve settlement for the /mint over-issue shortfall.

Records NOTE_BURN against towny-server for the full shortfall (ledger settlement).
Debits the live vault only up to available balance â€” the remainder is booked on the
ledger and reflected in note_supply via debt_repayment:over_issue_shortfall rows.

Dry-run by default; pass --apply to commit MySQL + D1 resync.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
REPAIR_SCRIPT = WORKSPACE / "Web Files" / "rootmc-api" / "scripts" / "repair-treasury-d1-canonical.py"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
DETAILS = "debt_repayment:over_issue_shortfall;one_time"


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


def vault_balance(cur) -> float:
    cur.execute(
        "SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1",
        (TOWNY_SERVER_UUID,),
    )
    row = cur.fetchone()
    return round(float(row[0]), 2) if row else 0.0


def repayment_exists(cur) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM root_treasury_ledger "
        "WHERE entry_type = 'NOTE_BURN' AND details = %s",
        (DETAILS,),
    )
    return int(cur.fetchone()[0]) > 0


def sync_d1() -> None:
    if not REPAIR_SCRIPT.is_file():
        print(f"WARN: missing {REPAIR_SCRIPT} â€” skip D1 sync", file=sys.stderr)
        return
    print("Syncing D1 canonical ledger ...")
    subprocess.run([sys.executable, str(REPAIR_SCRIPT)], check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--amount",
        type=float,
        required=True,
        help="Over-issue shortfall to settle (G)",
    )
    parser.add_argument("--apply", action="store_true", help="Commit MySQL + D1 sync")
    args = parser.parse_args()
    amount = round(max(0.0, float(args.amount)), 2)
    if amount < 0.01:
        print("Amount must be positive.")
        return 1

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    cur = conn.cursor()

    vault_before = vault_balance(cur)
    vault_debit = round(min(amount, vault_before), 2)
    ledger_only = round(amount - vault_debit, 2)

    print(f"Shortfall settlement: {amount:.2f} G")
    print(f"Vault before: {vault_before:.2f} G")
    print(f"Vault debit: {vault_debit:.2f} G")
    if ledger_only > 0.01:
        print(f"Ledger-only settlement: {ledger_only:.2f} G (note_supply adjusts via {DETAILS})")
    print(f"Details: {DETAILS}")

    if repayment_exists(cur):
        print("Already applied â€” skip.")
        conn.close()
        return 0

    if vault_before < 0.01 and amount > 0.01:
        print("Vault empty â€” ledger-only NOTE_BURN will still book the shortfall payment.")

    if not args.apply:
        print("\nDry-run only â€” pass --apply to commit.")
        conn.close()
        return 0

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn.begin()
        cur.execute(
            "INSERT INTO root_treasury_ledger "
            "(created_at, entry_type, amount, from_uuid, to_uuid, details) "
            "VALUES (%s, 'NOTE_BURN', %s, %s, NULL, %s)",
            (now, amount, TOWNY_SERVER_UUID, DETAILS),
        )
        if vault_debit > 0.01:
            cur.execute(
                "UPDATE root_economy_balances SET balance = balance - %s WHERE minecraft_uuid = %s",
                (vault_debit, TOWNY_SERVER_UUID),
            )
            if cur.rowcount != 1:
                raise RuntimeError("Failed to debit towny-server vault")
        conn.commit()
        print(f"Applied NOTE_BURN shortfall settlement: {amount:.2f} G")
        print(f"Vault after: {vault_balance(cur):.2f} G")
    except Exception as exc:
        conn.rollback()
        print(f"ROLLBACK: {exc}", file=sys.stderr)
        conn.close()
        return 1

    conn.close()
    try:
        sync_d1()
    except subprocess.CalledProcessError as exc:
        print(f"D1 sync failed: {exc}", file=sys.stderr)
        return 1
    print("Done. Deploy API + upload root-essentials jar for in-game /tax alignment.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
