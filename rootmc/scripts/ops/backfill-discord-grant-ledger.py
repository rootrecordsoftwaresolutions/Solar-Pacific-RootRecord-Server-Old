#!/usr/bin/env python3
"""Backfill MySQL treasury GRANT rows for applied Discord gold transfers.

Vault payouts already moved Gold (raw vault.transfer). This script adds the
missing ledger audit rows only â€” it does not debit towny-server again.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
WRANGLER_DIR = WORKSPACE / "Web Files" / "rootmc-api"
REPAIR_SCRIPT = WRANGLER_DIR / "scripts" / "repair-treasury-d1-canonical.py"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
DISCORD_SOURCES = ("discord_activity", "discord_link", "discord_first_message")
INFLOW_TYPES = frozenset({"OPENING", "TAX", "DEATH", "TOWNY_SINK", "LOAN_PRINCIPAL", "LOAN_INTEREST"})
OUTFLOW_TYPES = frozenset({"GRANT", "DIVIDEND", "LOAN_DISBURSE", "VOTE"})


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if key and value and key not in os.environ:
            os.environ[key] = value


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


def d1_query(sql: str) -> list[dict]:
    token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    if not token:
        raise RuntimeError("CLOUDFLARE_API_TOKEN required (RootMC Workspace/.env)")
    sql_one_line = " ".join(sql.split())
    cmd = (
        f'npx wrangler d1 execute rootmc --remote --json '
        f'--command "{sql_one_line.replace(chr(34), chr(92) + chr(34))}"'
    )
    res = subprocess.run(
        cmd,
        cwd=WRANGLER_DIR,
        env=dict(os.environ),
        check=True,
        capture_output=True,
        text=True,
        shell=True,
    )
    payload = json.loads(res.stdout)
    if not payload or not payload[0].get("success"):
        raise RuntimeError(f"D1 query failed: {res.stdout}")
    return payload[0].get("results") or []


def iso_to_mysql(ts: str | None) -> str:
    if not ts:
        return dt.datetime.now(dt.UTC).strftime("%Y-%m-%d %H:%M:%S")
    text = str(ts).strip().replace("Z", "+00:00")
    try:
        when = dt.datetime.fromisoformat(text)
    except ValueError:
        return dt.datetime.now(dt.UTC).strftime("%Y-%m-%d %H:%M:%S")
    if when.tzinfo is not None:
        when = when.astimezone(dt.UTC).replace(tzinfo=None)
    return when.strftime("%Y-%m-%d %H:%M:%S")


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


def fetch_applied_transfers() -> list[dict]:
    sources_sql = ", ".join(f"'{s}'" for s in DISCORD_SOURCES)
    rows = d1_query(
        f"SELECT id, to_uuid, amount, source, created_at, applied_at "
        f"FROM rootmc_gold_transfers "
        f"WHERE status = 'applied' AND source IN ({sources_sql}) "
        f"AND LOWER(from_uuid) = '{TOWNY_SERVER_UUID.lower()}' "
        f"ORDER BY COALESCE(applied_at, created_at) ASC"
    )
    return rows


def existing_transfer_ids(cur) -> set[str]:
    cur.execute(
        """
        SELECT details FROM root_treasury_ledger
        WHERE entry_type = 'GRANT' AND details LIKE '%;transfer=%'
        """
    )
    out: set[str] = set()
    for (details,) in cur.fetchall():
        text = str(details or "")
        m = re.search(r";transfer=([^;]+)", text)
        if m:
            out.add(m.group(1).strip().lower())
    return out


def grant_details(source: str, transfer_id: str) -> str:
    return f"operator=?;{source};transfer={transfer_id}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Insert rows and resync D1 (default: dry-run)")
    args = parser.parse_args()

    load_dotenv(WORKSPACE / ".env")
    transfers = fetch_applied_transfers()
    print(f"Applied Discord treasury transfers in D1: {len(transfers)}")

    cfg = read_db_yaml()
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **cfg)
    try:
        cur = conn.cursor()
        before_net = ledger_net(cur)
        before_vault = vault_balance(cur)
        print(f"Before â€” vault: {before_vault} G | ledger net: {before_net} G | gap: {round(before_vault - before_net, 2)} G")

        known = existing_transfer_ids(cur)
        to_insert: list[tuple] = []
        skipped = 0
        for row in transfers:
            transfer_id = str(row.get("id") or "").strip().lower()
            to_uuid = str(row.get("to_uuid") or "").strip().lower()
            source = str(row.get("source") or "").strip()
            amount = round(float(row.get("amount") or 0), 2)
            if not transfer_id or not to_uuid or amount <= 0 or source not in DISCORD_SOURCES:
                skipped += 1
                continue
            if transfer_id in known:
                skipped += 1
                continue
            created = iso_to_mysql(row.get("applied_at") or row.get("created_at"))
            to_insert.append(
                (amount, TOWNY_SERVER_UUID, to_uuid, grant_details(source, transfer_id), created, transfer_id, source)
            )

        print(f"Already backfilled / skipped: {skipped}")
        print(f"Missing ledger rows to insert: {len(to_insert)}")
        if to_insert:
            total = round(sum(r[0] for r in to_insert), 2)
            by_source: dict[str, int] = {}
            for row in to_insert:
                by_source[row[6]] = by_source.get(row[6], 0) + 1
            print(f"  Total Gold: {total} G")
            for source, count in sorted(by_source.items()):
                print(f"  {source}: {count}")

        if not args.apply:
            print("\nDry-run only. Re-run with --apply to insert and resync D1.")
            return 0

        inserted = 0
        for amount, from_uuid, to_uuid, details, created_at, transfer_id, _source in to_insert:
            cur.execute(
                """
                INSERT INTO root_treasury_ledger
                  (entry_type, amount, from_uuid, to_uuid, details, created_at)
                VALUES ('GRANT', %s, %s, %s, %s, %s)
                """,
                (amount, from_uuid, to_uuid, details, created_at),
            )
            inserted += 1
            known.add(transfer_id)

        conn.commit()
        after_net = ledger_net(cur)
        after_vault = vault_balance(cur)
        print(f"\nInserted {inserted} GRANT row(s).")
        print(f"After â€” vault: {after_vault} G | ledger net: {after_net} G | gap: {round(after_vault - after_net, 2)} G")
    finally:
        conn.close()

    print("\nResyncing D1 from MySQL ...")
    result = subprocess.run([sys.executable, str(REPAIR_SCRIPT)], cwd=WRANGLER_DIR, env=dict(os.environ))
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
