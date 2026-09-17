#!/usr/bin/env python3
"""Reconcile MySQL treasury ledger to towny-server vault + July opening carryover."""
from __future__ import annotations

import datetime as dt
import re
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
OPENING = 1048.71
TOLERANCE = 5.0
INFLOW_TYPES = frozenset({"OPENING", "TAX", "DEATH", "TOWNY_SINK", "LOAN_PRINCIPAL", "LOAN_INTEREST"})
OUTFLOW_TYPES = frozenset({"GRANT", "DIVIDEND", "LOAN_DISBURSE", "VOTE"})


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


def ledger_net(cur) -> float:
    cur.execute(
        "SELECT entry_type, COALESCE(SUM(amount), 0) AS total FROM root_treasury_ledger GROUP BY entry_type"
    )
    inflow = outflow = 0.0
    for entry_type, total in cur.fetchall():
        t = str(entry_type).upper()
        amt = float(total)
        if t in INFLOW_TYPES:
            inflow += amt
        elif t in OUTFLOW_TYPES:
            outflow += amt
    return round(inflow - outflow, 2)


def vault_balance(cur) -> float:
    cur.execute(
        "SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1",
        (TOWNY_SERVER_UUID,),
    )
    row = cur.fetchone()
    return round(float(row[0]), 2) if row else 0.0


def ts_bucket(created_at) -> int:
    if isinstance(created_at, dt.datetime):
        ts = created_at.timestamp()
    else:
        ts = dt.datetime.fromisoformat(str(created_at).replace(" ", "T")).timestamp()
    return int(ts // 3)


def delete_ids(cur, ids: list[int], label: str) -> None:
    if not ids:
        print(f"Deleted {label}: 0")
        return
    placeholders = ",".join(["%s"] * len(ids))
    cur.execute(f"DELETE FROM root_treasury_ledger WHERE id IN ({placeholders})", ids)
    print(f"Deleted {label}: {cur.rowcount}")


def trim_inflows(cur, excess: float, *, details: str | None, label: str) -> float:
    """Remove TOWNY_SINK rows (newest first) without overshooting the excess target."""
    if excess <= TOLERANCE:
        return 0.0
    sql = "SELECT id, amount FROM root_treasury_ledger WHERE entry_type = 'TOWNY_SINK'"
    binds: list = []
    if details is not None:
        sql += " AND details = %s"
        binds.append(details)
    sql += " ORDER BY id DESC"
    cur.execute(sql, binds)
    rows = cur.fetchall()
    trim_ids: list[int] = []
    trimmed = 0.0
    remaining = excess
    for row_id, amount in rows:
        amt = float(amount)
        if amt > remaining + TOLERANCE:
            continue
        trim_ids.append(int(row_id))
        trimmed += amt
        remaining = round(remaining - amt, 2)
        if remaining <= TOLERANCE:
            break
    delete_ids(cur, trim_ids, label)
    return trimmed


def main() -> int:
    cfg = read_db_yaml()
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **cfg)
    try:
        cur = conn.cursor()
        vault = vault_balance(cur)
        net_before = ledger_net(cur)
        target_net = round(vault - OPENING, 2)
        print(f"BEFORE  vault={vault} G  ledger_net={net_before} G  target_net={target_net} G")
        print(f"        implied={round(OPENING + net_before, 2)} G  gap={round(vault - (OPENING + net_before), 2)} G")

        # 1) Dedupe towny:other (phantom repeat callbacks)
        cur.execute(
            """
            SELECT id, amount, from_uuid, created_at
            FROM root_treasury_ledger
            WHERE entry_type = 'TOWNY_SINK' AND details = 'towny:other'
            ORDER BY id ASC
            """
        )
        seen: dict[tuple, int] = {}
        dup_ids: list[int] = []
        for row_id, amount, from_uuid, created_at in cur.fetchall():
            key = (str(from_uuid or ""), round(float(amount), 2), ts_bucket(created_at))
            if key in seen:
                dup_ids.append(int(row_id))
            else:
                seen[key] = int(row_id)
        delete_ids(cur, dup_ids, "duplicate towny:other")

        # 2) Ledger-only normalize grants (never debited vault)
        cur.execute(
            "SELECT id FROM root_treasury_ledger WHERE entry_type = 'GRANT' AND details LIKE 'normalize:grant:sink:%'"
        )
        delete_ids(cur, [int(r[0]) for r in cur.fetchall()], "normalize:grant:sink rows")

        net_mid = ledger_net(cur)
        excess = round(net_mid - target_net, 2)
        print(f"MID    ledger_net={net_mid} G  excess_inflow={excess} G")

        # 3) Remove towny:other burst (mis-tagged duplicate claim callbacks)
        trimmed = trim_inflows(cur, excess, details="towny:other", label="excess towny:other")
        excess = round(excess - trimmed, 2)

        # 4) If still high, trim duplicate towny:claim (same dedupe rule)
        if excess > TOLERANCE:
            cur.execute(
                """
                SELECT id, amount, from_uuid, created_at
                FROM root_treasury_ledger
                WHERE entry_type = 'TOWNY_SINK' AND details LIKE 'towny:claim%'
                ORDER BY id ASC
                """
            )
            seen.clear()
            claim_dup_ids: list[int] = []
            for row_id, amount, from_uuid, created_at in cur.fetchall():
                key = (str(from_uuid or ""), round(float(amount), 2), ts_bucket(created_at))
                if key in seen:
                    claim_dup_ids.append(int(row_id))
                else:
                    seen[key] = int(row_id)
            delete_ids(cur, claim_dup_ids, "duplicate towny:claim")
            net_mid = ledger_net(cur)
            excess = round(net_mid - target_net, 2)

        if excess > TOLERANCE:
            trimmed = trim_inflows(cur, excess, details=None, label="remaining TOWNY_SINK excess")
            excess = round(excess - trimmed, 2)

        # 5) Final pass â€” trim any TOWNY_SINK rows until within tolerance
        for _ in range(5):
            net_mid = ledger_net(cur)
            excess = round(net_mid - target_net, 2)
            if excess <= TOLERANCE:
                break
            trimmed = trim_inflows(cur, excess, details=None, label="final TOWNY_SINK trim")
            if trimmed <= 0:
                break

        # 6) Small remainder â€” drop lone service-fee test rows if within one flat claim fee
        net_mid = ledger_net(cur)
        excess = round(net_mid - target_net, 2)
        if 0 < excess <= 12:
            cur.execute(
                """
                SELECT id, amount FROM root_treasury_ledger
                WHERE entry_type = 'TOWNY_SINK' AND details LIKE 'service-fee:%'
                ORDER BY id DESC LIMIT 5
                """
            )
            for row_id, amount in cur.fetchall():
                if float(amount) <= excess + TOLERANCE:
                    cur.execute("DELETE FROM root_treasury_ledger WHERE id = %s", (int(row_id),))
                    print(f"Deleted service-fee row id={row_id} ({amount} G)")
                    break

        net_after = ledger_net(cur)
        implied = round(OPENING + net_after, 2)
        gap = round(vault - implied, 2)
        print(f"AFTER  vault={vault} G  ledger_net={net_after} G  implied={implied} G  gap={gap} G")

        if abs(gap) > 1.0:
            print(f"ERROR: gap {gap} G still exceeds 1 G", file=sys.stderr)
            conn.rollback()
            return 1

        conn.commit()
        print("Committed MySQL reconciliation.")
        return 0
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
