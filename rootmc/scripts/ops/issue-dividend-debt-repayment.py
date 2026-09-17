#!/usr/bin/env python3
"""One-time debt repayment: burn Reserve Notes equal to the current dividend pool.

Uses the same pool math as Activity Dividend (50% of positive monthly reserve net).
Inserts NOTE_BURN from towny-server vault â€” retires unbacked supply instead of player payouts.

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
PAYOUT_RATIO = 0.5
INFLOW_TYPES = ("OPENING", "TAX", "DEATH", "TOWNY_SINK", "LOAN_PRINCIPAL", "LOAN_INTEREST")
OUTFLOW_TYPES = ("GRANT", "DIVIDEND", "LOAN_DISBURSE", "VOTE")
DEFAULT_MONTH = "2026-07"
DETAILS_PREFIX = "debt_repayment:dividend_pool"


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


def month_bounds_hst(month_key: str) -> tuple[str, str]:
    y, m = map(int, month_key.split("-"))
    if m == 12:
        end_y, end_m = y + 1, 1
    else:
        end_y, end_m = y, m + 1
    start = f"{y:04d}-{m:02d}-01 10:00:00"
    end = f"{end_y:04d}-{end_m:02d}-01 10:00:00"
    return start, end


def vault_balance(cur) -> float:
    cur.execute(
        "SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1",
        (TOWNY_SERVER_UUID,),
    )
    row = cur.fetchone()
    return round(float(row[0]), 2) if row else 0.0


def month_net(cur, month_key: str) -> float:
    start, end = month_bounds_hst(month_key)
    in_ph = ",".join(["%s"] * len(INFLOW_TYPES))
    out_ph = ",".join(["%s"] * len(OUTFLOW_TYPES))
    cur.execute(
        f"SELECT COALESCE(SUM(amount), 0) FROM root_treasury_ledger "
        f"WHERE entry_type IN ({in_ph}) AND created_at >= %s AND created_at < %s",
        (*INFLOW_TYPES, start, end),
    )
    inflow = float(cur.fetchone()[0])
    cur.execute(
        f"SELECT COALESCE(SUM(amount), 0) FROM root_treasury_ledger "
        f"WHERE entry_type IN ({out_ph}) AND created_at >= %s AND created_at < %s",
        (*OUTFLOW_TYPES, start, end),
    )
    outflow = float(cur.fetchone()[0])
    return round(inflow - outflow, 2)


def dividend_pool(net: float) -> float:
    if net < -0.01:
        return 0.0
    return round(max(0.0, net) * PAYOUT_RATIO, 2)


def repayment_exists(cur, details: str) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM root_treasury_ledger "
        "WHERE entry_type = 'NOTE_BURN' AND details = %s",
        (details,),
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
    parser.add_argument("--month", default=DEFAULT_MONTH, help="HST month key (YYYY-MM)")
    parser.add_argument(
        "--amount",
        type=float,
        default=None,
        help="Explicit NOTE_BURN amount (G); overrides dividend pool math",
    )
    parser.add_argument(
        "--details",
        default="",
        help="Ledger details suffix (default: debt_repayment:dividend_pool;month=â€¦;one_time)",
    )
    parser.add_argument("--apply", action="store_true", help="Commit MySQL + D1 sync")
    args = parser.parse_args()
    month_key = args.month.strip()

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    cur = conn.cursor()

    vault_before = vault_balance(cur)
    net = month_net(cur, month_key)
    pool = dividend_pool(net)
    if args.amount is not None:
        pool = round(max(0.0, float(args.amount)), 2)
    if args.details.strip():
        details = args.details.strip()
    else:
        details = f"{DETAILS_PREFIX};month={month_key};one_time"

    print(f"Month: {month_key} (HST)")
    print(f"Reserve net: {net:.2f} G")
    if args.amount is None:
        print(f"Dividend pool (50%): {pool:.2f} G")
    else:
        print(f"Explicit burn amount: {pool:.2f} G")
    print(f"Vault before: {vault_before:.2f} G")

    if repayment_exists(cur, details):
        print(f"Already applied ({details}) â€” skip.")
        conn.close()
        return 0

    if args.amount is None and net < -0.01:
        print("Reserve net is negative â€” no debt repayment (matches no-dividend policy).")
        conn.close()
        return 0

    if pool < 0.01:
        print("Dividend pool is zero â€” nothing to repay.")
        conn.close()
        return 0

    if vault_before < pool - 0.01:
        print(f"ERROR: vault {vault_before:.2f} G cannot cover burn {pool:.2f} G", file=sys.stderr)
        conn.close()
        return 1

    print(f"Will NOTE_BURN {pool:.2f} G from towny-server ({details})")
    print(f"Vault after: {vault_before - pool:.2f} G")

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
            (now, pool, TOWNY_SERVER_UUID, details),
        )
        cur.execute(
            "UPDATE root_economy_balances SET balance = balance - %s WHERE minecraft_uuid = %s",
            (pool, TOWNY_SERVER_UUID),
        )
        if cur.rowcount != 1:
            raise RuntimeError("Failed to debit towny-server vault")
        conn.commit()
        print(f"Applied NOTE_BURN debt repayment: {pool:.2f} G")
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
    print("Done. Redeploy API if treasury handlers changed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
