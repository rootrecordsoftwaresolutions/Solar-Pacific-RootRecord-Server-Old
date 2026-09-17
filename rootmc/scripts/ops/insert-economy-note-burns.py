#!/usr/bin/env python3
"""Insert NOTE_BURN corrections for gold-backed economy alignment.

1. Reclassify post-July dynamic tax (TAX â†’ NOTE_BURN) and debit towny-server vault.
2. Reclassify legacy reserve donations (DONATION â†’ NOTE_BURN) and debit vault (reverse incorrect credit).
3. Optional: opening carryover write-down (--opening-burn).

Dry-run by default; pass --apply to commit MySQL changes.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
OPENING_BURN_G = 1049.23
POST_RESET_START = "2026-07-01 00:00:00"
OPENING_BURN_AT = "2026-07-01 10:00:00"


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


def sum_tax(cur) -> tuple[float, list[int]]:
    cur.execute(
        "SELECT id, amount FROM root_treasury_ledger "
        "WHERE entry_type = 'TAX' AND amount > 0 AND created_at >= %s",
        (POST_RESET_START,),
    )
    rows = cur.fetchall()
    total = round(sum(float(r[1]) for r in rows), 2)
    ids = [int(r[0]) for r in rows]
    return total, ids


def sum_donations(cur) -> tuple[float, list[int]]:
    cur.execute(
        "SELECT id, amount FROM root_treasury_ledger "
        "WHERE entry_type = 'DONATION' AND amount > 0"
    )
    rows = cur.fetchall()
    total = round(sum(float(r[1]) for r in rows), 2)
    ids = [int(r[0]) for r in rows]
    return total, ids


def opening_burn_exists(cur) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM root_treasury_ledger "
        "WHERE entry_type = 'NOTE_BURN' AND details LIKE 'opening:map262_unbacked_carryover%'",
    )
    return int(cur.fetchone()[0]) > 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Commit MySQL changes")
    parser.add_argument(
        "--opening-burn",
        action="store_true",
        help=f"Also insert opening carryover NOTE_BURN ({OPENING_BURN_G} G)",
    )
    args = parser.parse_args()

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    cur = conn.cursor()

    vault_before = vault_balance(cur)
    tax_total, tax_ids = sum_tax(cur)
    donation_total, donation_ids = sum_donations(cur)
    print(f"Vault before: {vault_before:.2f} G")
    print(f"Post-July TAX to reclassify: {tax_total:.2f} G ({len(tax_ids)} rows)")
    print(f"Legacy DONATION to reclassify: {donation_total:.2f} G ({len(donation_ids)} rows)")

    opening_amount = 0.0
    if args.opening_burn:
        if opening_burn_exists(cur):
            print("Opening NOTE_BURN already present - skip")
        else:
            opening_amount = OPENING_BURN_G
            print(f"Opening carryover burn: {opening_amount:.2f} G")

    total_debit = round(tax_total + donation_total + opening_amount, 2)
    print(f"Total vault debit: {total_debit:.2f} G -> vault after {vault_before - total_debit:.2f} G")

    if total_debit <= 0:
        print("Nothing to apply.")
        conn.close()
        return 0

    if vault_before < total_debit - 0.01:
        print(f"ERROR: vault {vault_before:.2f} G cannot cover debit {total_debit:.2f} G", file=sys.stderr)
        conn.close()
        return 1

    if not args.apply:
        print("\nDry-run only â€” pass --apply to commit.")
        conn.close()
        return 0

    try:
        conn.begin()
        if tax_total > 0.01 and tax_ids:
            placeholders = ",".join(["%s"] * len(tax_ids))
            cur.execute(
                f"UPDATE root_treasury_ledger SET entry_type = 'NOTE_BURN', "
                f"details = CONCAT('correction:tax_reclass:', COALESCE(details, '')) "
                f"WHERE id IN ({placeholders})",
                tax_ids,
            )
            print(f"Reclassified TAX to NOTE_BURN: {cur.rowcount} rows")

        if donation_total > 0.01 and donation_ids:
            placeholders = ",".join(["%s"] * len(donation_ids))
            cur.execute(
                f"UPDATE root_treasury_ledger SET entry_type = 'NOTE_BURN', "
                f"details = 'correction:donation_reclass:donation' "
                f"WHERE id IN ({placeholders})",
                donation_ids,
            )
            print(f"Reclassified DONATION to NOTE_BURN: {cur.rowcount} rows")

        if opening_amount > 0.01:
            cur.execute(
                "INSERT INTO root_treasury_ledger "
                "(created_at, entry_type, amount, from_uuid, to_uuid, details) "
                "VALUES (%s, 'NOTE_BURN', %s, NULL, NULL, %s)",
                (
                    OPENING_BURN_AT,
                    opening_amount,
                    "opening:map262_unbacked_carryover;map262_true_reserve_opening",
                ),
            )
            print(f"Inserted opening NOTE_BURN: {opening_amount:.2f} G")

        cur.execute(
            "UPDATE root_economy_balances SET balance = balance - %s "
            "WHERE minecraft_uuid = %s",
            (total_debit, TOWNY_SERVER_UUID),
        )
        if cur.rowcount != 1:
            raise RuntimeError("Failed to debit towny-server vault")

        conn.commit()
        vault_after = vault_balance(cur)
        print(f"Applied. Vault after: {vault_after:.2f} G")
        print("Next: repair-treasury-d1-canonical.py to sync D1 + redeploy API if needed.")
    except Exception as exc:
        conn.rollback()
        print(f"ROLLBACK: {exc}", file=sys.stderr)
        conn.close()
        return 1

    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
