#!/usr/bin/env python3
"""Zero player wallet(s) and log NOTE_BURN (destroys Notes from circulation).

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
DETAILS_PREFIX = "governance:wallet_forfeit"


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


def player_balance(cur, uuid: str) -> tuple[float, str | None]:
    cur.execute(
        "SELECT balance, minecraft_username FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1",
        (uuid,),
    )
    row = cur.fetchone()
    if not row:
        return 0.0, None
    return round(float(row[0]), 2), str(row[1] or "").strip() or None


def burn_exists(cur, uuid: str) -> bool:
    details = f"{DETAILS_PREFIX};uuid={uuid}"
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
    parser.add_argument(
        "players",
        nargs="+",
        help="minecraft_username or uuid",
    )
    parser.add_argument("--apply", action="store_true", help="Commit MySQL + D1 sync")
    args = parser.parse_args()

    conn = pymysql.connect(charset="utf8mb4", **read_db_yaml())
    cur = conn.cursor()

    targets: list[tuple[str, str]] = []
    for raw in args.players:
        token = raw.strip()
        if re.match(r"^[0-9a-f-]{36}$", token, re.I):
            bal, name = player_balance(cur, token.lower())
            targets.append((token.lower(), name or token))
        else:
            cur.execute(
                "SELECT minecraft_uuid, minecraft_username, balance FROM root_economy_balances "
                "WHERE LOWER(minecraft_username) = LOWER(%s) LIMIT 1",
                (token,),
            )
            row = cur.fetchone()
            if not row:
                print(f"ERROR: no balance row for {token}", file=sys.stderr)
                conn.close()
                return 1
            targets.append((str(row[0]).lower(), str(row[1] or token)))
            bal = round(float(row[2]), 2)
        uuid, name = targets[-1]
        print(f"{name} ({uuid}): balance {bal:.2f} G")

    total = 0.0
    plan: list[tuple[str, str, float]] = []
    for uuid, name in targets:
        if burn_exists(cur, uuid):
            print(f"  SKIP {name} â€” forfeit burn already applied")
            continue
        bal, _ = player_balance(cur, uuid)
        if bal < 0.01:
            print(f"  SKIP {name} â€” already zero")
            continue
        plan.append((uuid, name, bal))
        total += bal

    total = round(total, 2)
    print(f"Total to burn: {total:.2f} G ({len(plan)} player(s))")

    if not plan:
        conn.close()
        return 0

    if not args.apply:
        print("\nDry-run only â€” pass --apply to commit.")
        conn.close()
        return 0

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    try:
        conn.begin()
        for uuid, name, amount in plan:
            details = f"{DETAILS_PREFIX};uuid={uuid};player={name}"
            cur.execute(
                "SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s FOR UPDATE",
                (uuid,),
            )
            row = cur.fetchone()
            if not row:
                raise RuntimeError(f"No balance row for {name}")
            live = round(float(row[0]), 2)
            if live < 0.01:
                continue
            burn = live
            cur.execute(
                "UPDATE root_economy_balances SET balance = 0, minecraft_username = %s "
                "WHERE minecraft_uuid = %s",
                (name, uuid),
            )
            cur.execute(
                "INSERT INTO root_treasury_ledger "
                "(created_at, entry_type, amount, from_uuid, to_uuid, details) "
                "VALUES (%s, 'NOTE_BURN', %s, %s, NULL, %s)",
                (now, burn, uuid, details),
            )
            print(f"Burned {burn:.2f} G from {name}")
        conn.commit()
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
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
