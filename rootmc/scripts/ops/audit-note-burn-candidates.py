#!/usr/bin/env python3
"""Audit TAX vs NOTE_BURN and economy totals for correction burns."""
from __future__ import annotations

import re
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
OPENING = 1049.23
POST_RESET_START = "2026-07-01 00:00:00"


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


def main() -> None:
    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    cur = conn.cursor()

    print("=== TAX rows (amount > 0) ===")
    cur.execute(
        "SELECT id, amount, details, created_at FROM root_treasury_ledger "
        "WHERE entry_type = 'TAX' AND amount > 0 ORDER BY id"
    )
    tax_total = 0.0
    for row in cur.fetchall():
        tax_total += float(row[1])
        print(row)
    print(f"TAX total: {tax_total:.2f}")

    cur.execute(
        "SELECT COALESCE(SUM(amount), 0) FROM root_treasury_ledger WHERE entry_type = 'NOTE_BURN'"
    )
    print(f"NOTE_BURN total: {float(cur.fetchone()[0]):.2f}")

    print("\n=== Ledger by type ===")
    cur.execute(
        "SELECT entry_type, ROUND(SUM(amount), 2), COUNT(*) "
        "FROM root_treasury_ledger GROUP BY entry_type ORDER BY entry_type"
    )
    inflow_types = {"OPENING", "TAX", "DEATH", "TOWNY_SINK", "LOAN_PRINCIPAL", "LOAN_INTEREST", "DONATION"}
    outflow_types = {"GRANT", "DIVIDEND", "LOAN_DISBURSE", "VOTE"}
    inflow = outflow = 0.0
    for entry_type, total, count in cur.fetchall():
        t = str(entry_type).upper()
        amt = float(total)
        if t in inflow_types:
            inflow += amt
        elif t in outflow_types:
            outflow += amt
        print(f"  {entry_type}: {total} ({count} rows)")
    ledger_net = round(inflow - outflow, 2)
    print(f"Ledger net (inflow - outflow): {ledger_net:.2f}")

    cur.execute(
        "SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1",
        (TOWNY_SERVER_UUID,),
    )
    vault = float(cur.fetchone()[0])
    cur.execute(
        "SELECT ROUND(SUM(balance), 2) FROM root_economy_balances "
        "WHERE minecraft_uuid != %s",
        (TOWNY_SERVER_UUID,),
    )
    players = float(cur.fetchone()[0] or 0)
    total_notes = round(vault + players, 2)
    implied = round(OPENING + ledger_net, 2)
    gap = round(vault - implied, 2)

    print(f"\nVault: {vault:.2f}")
    print(f"Player wallets: {players:.2f}")
    print(f"Total Notes: {total_notes:.2f}")
    print(f"Opening + ledger net: {implied:.2f}")
    print(f"Vault vs opening+ledger gap: {gap:.2f}")

    cur.execute(
        "SELECT ROUND(SUM(amount), 2) FROM root_treasury_ledger "
        "WHERE entry_type = 'TAX' AND amount > 0 AND created_at >= %s",
        (POST_RESET_START,),
    )
    post_tax = float(cur.fetchone()[0] or 0)
    print(f"\nPost-July TAX to reclassify: {post_tax:.2f}")

    conn.close()


if __name__ == "__main__":
    main()
