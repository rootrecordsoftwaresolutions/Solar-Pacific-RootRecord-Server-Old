#!/usr/bin/env python3
"""Normalize inflated Towny claim + operator grant rows in MySQL, then resync D1 from MySQL.

Does NOT insert ledger-only GRANT rows. For full vault reconciliation run reconcile-treasury-ledger.py first.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
WRANGLER_DIR = WORKSPACE / "Web Files" / "rootmc-api"
WRANGLER_DB = "rootmc"
TREASURY_SERVER_ID = "rootmc"
TOWNY_SERVER_UUID = "a73f39b0-1b7c-2930-b4a3-ce101812d926"
HEARTBEAT_SERVER_ID = "15bbc057-4f8b-4761-abdb-7b7e4d9c7512"
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"

CLAIM_FLAT_FEE = 12
# Never inflate operator grants â€” only cap stray rows to the flat claim fee.
OPERATOR_GRANT_CAP = CLAIM_FLAT_FEE
TOWNY_NEW_TOWN = 400
TOWNY_NEW_NATION = 2000


def read_db_yaml() -> dict[str, str]:
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


def d1_exec(sql: str):
    token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    if not token:
        raise RuntimeError("CLOUDFLARE_API_TOKEN required (load RootMC Workspace\\.env)")
    sql_one_line = " ".join(sql.split())
    cmd = (
        f'npx wrangler d1 execute {WRANGLER_DB} --remote --json '
        f'--command "{sql_one_line.replace(chr(34), chr(92) + chr(34))}"'
    )
    res = subprocess.run(
        cmd,
        cwd=WRANGLER_DIR,
        env=os.environ.copy(),
        check=True,
        capture_output=True,
        text=True,
        shell=True,
    )
    payload = json.loads(res.stdout)
    if not payload or not payload[0].get("success"):
        raise RuntimeError(f"D1 failed: {res.stdout}")
    return payload[0].get("results", [])


def d1_exec_file(sql: str):
    token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    if not token:
        raise RuntimeError("CLOUDFLARE_API_TOKEN required")
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as f:
        f.write(sql)
        sql_path = f.name
    try:
        cmd = f'npx wrangler d1 execute {WRANGLER_DB} --remote --json --file "{sql_path}"'
        res = subprocess.run(cmd, cwd=WRANGLER_DIR, env=os.environ.copy(), capture_output=True, text=True, shell=True)
        if res.returncode != 0:
            raise RuntimeError(res.stderr or res.stdout)
        out = res.stdout.strip()
        json_start = out.find("[")
        if json_start < 0:
            raise RuntimeError(f"D1 non-JSON: {out}")
        payload = json.loads(out[json_start:])
        if not payload or not payload[0].get("success"):
            raise RuntimeError(f"D1 file failed: {res.stdout}")
    finally:
        try:
            os.remove(sql_path)
        except OSError:
            pass


def normalize_mysql(conn) -> dict[str, int]:
    cur = conn.cursor()
    stats: dict[str, int] = {}

    cur.execute(
        """
        UPDATE root_treasury_ledger
        SET amount = %s
        WHERE entry_type = 'TOWNY_SINK'
          AND (details LIKE 'towny:claim%%' OR details LIKE 'backfill:towny:claim%%')
          AND amount > %s
        """,
        (CLAIM_FLAT_FEE, CLAIM_FLAT_FEE),
    )
    stats["claims_normalized"] = cur.rowcount

    cur.execute(
        """
        UPDATE root_treasury_ledger
        SET amount = %s
        WHERE entry_type = 'TOWNY_SINK'
          AND details = 'towny:other'
          AND amount > %s
          AND ABS(amount - %s) >= 1
          AND ABS(amount - %s) >= 1
        """,
        (CLAIM_FLAT_FEE, CLAIM_FLAT_FEE, TOWNY_NEW_TOWN, TOWNY_NEW_NATION),
    )
    stats["towny_other_pollution_normalized"] = cur.rowcount

    cur.execute(
        """
        UPDATE root_treasury_ledger
        SET amount = %s
        WHERE entry_type = 'GRANT'
          AND details LIKE 'operator=%%'
          AND details NOT LIKE '%%tier=%%'
          AND details NOT LIKE 'normalize:grant:%%'
          AND amount > %s
        """,
        (OPERATOR_GRANT_CAP, OPERATOR_GRANT_CAP),
    )
    stats["grants_normalized"] = cur.rowcount

    conn.commit()
    return stats


def normalize_d1_direct() -> dict[str, int]:
    stats: dict[str, int] = {}
    for label, sql in [
        (
            "claims_normalized",
            f"""
            UPDATE rootmc_treasury_ledger
            SET amount = {CLAIM_FLAT_FEE}
            WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'
              AND entry_type = 'TOWNY_SINK'
              AND (details LIKE 'towny:claim%' OR details LIKE 'backfill:towny:claim%')
              AND amount > {CLAIM_FLAT_FEE}
            """,
        ),
        (
            "towny_other_pollution_normalized",
            f"""
            UPDATE rootmc_treasury_ledger
            SET amount = {CLAIM_FLAT_FEE}
            WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'
              AND entry_type = 'TOWNY_SINK'
              AND details = 'towny:other'
              AND amount > {CLAIM_FLAT_FEE}
              AND ABS(amount - {TOWNY_NEW_TOWN}) >= 1
              AND ABS(amount - {TOWNY_NEW_NATION}) >= 1
            """,
        ),
        (
            "grants_normalized",
            f"""
            UPDATE rootmc_treasury_ledger
            SET amount = {OPERATOR_GRANT_CAP}
            WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'
              AND entry_type = 'GRANT'
              AND details LIKE 'operator=%'
              AND details NOT LIKE '%tier=%'
              AND details NOT LIKE 'normalize:grant:%'
              AND amount > {OPERATOR_GRANT_CAP}
            """,
        ),
    ]:
        before = d1_exec(
            f"SELECT COUNT(*) AS c FROM rootmc_treasury_ledger WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'"
        )
        d1_exec(sql)
        # wrangler UPDATE doesn't return rowcount reliably; run a count query for the clause
        if "towny:claim" in sql:
            check = d1_exec(
                f"SELECT COUNT(*) AS c FROM rootmc_treasury_ledger "
                f"WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}' AND entry_type = 'TOWNY_SINK' "
                f"AND (details LIKE 'towny:claim%' OR details LIKE 'backfill:towny:claim%') AND amount > {CLAIM_FLAT_FEE}"
            )
        elif "towny:other" in sql:
            check = d1_exec(
                f"SELECT COUNT(*) AS c FROM rootmc_treasury_ledger "
                f"WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}' AND entry_type = 'TOWNY_SINK' "
                f"AND details = 'towny:other' AND amount > {CLAIM_FLAT_FEE} "
                f"AND ABS(amount - {TOWNY_NEW_TOWN}) >= 1 AND ABS(amount - {TOWNY_NEW_NATION}) >= 1"
            )
        else:
            check = d1_exec(
                f"SELECT COUNT(*) AS c FROM rootmc_treasury_ledger "
                f"WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}' AND entry_type = 'GRANT' "
                f"AND details LIKE 'operator=%' AND details NOT LIKE '%tier=%' AND amount > {OPERATOR_GRANT_CAP}"
            )
        stats[label] = int(check[0]["c"]) if check else 0
        _ = before
    return stats


def sync_mysql_ledger_to_d1(conn, *, replace: bool = False) -> int:
    synced = dt.datetime.now(dt.UTC).isoformat().replace("+00:00", "Z")
    if replace:
        print("Clearing canonical D1 treasury ledger before full rebuild ...")
        d1_exec(f"DELETE FROM rootmc_treasury_ledger WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'")
    cur = conn.cursor()
    cur.execute(
        "SELECT id, entry_type, amount, from_uuid, to_uuid, details, created_at "
        "FROM root_treasury_ledger WHERE id > 0 ORDER BY id ASC"
    )
    batch: list[str] = []
    count = 0

    def flush():
        nonlocal count
        if not batch:
            return
        d1_exec_file(";\n".join(batch) + ";")
        count += len(batch)
        batch.clear()

    for mysql_id, entry_type, amount, from_uuid, to_uuid, details, created_at in cur.fetchall():
        mysql_id = int(mysql_id)
        amt = round(float(amount), 2)
        if amt <= 0:
            continue
        created = created_at.isoformat().replace("+00:00", "Z") if hasattr(created_at, "isoformat") else str(created_at)
        from_sql = "NULL" if not from_uuid else f"'{sql_escape(str(from_uuid))}'"
        to_sql = "NULL" if not to_uuid else f"'{sql_escape(str(to_uuid))}'"
        det_sql = "NULL" if not details else f"'{sql_escape(str(details)[:512])}'"
        batch.append(
            "INSERT INTO rootmc_treasury_ledger "
            "(server_id, mysql_id, entry_type, amount, from_uuid, to_uuid, details, created_at, synced_at) "
            f"VALUES ('{sql_escape(TREASURY_SERVER_ID)}', {mysql_id}, '{sql_escape(str(entry_type).upper())}', {amt}, "
            f"{from_sql}, {to_sql}, {det_sql}, '{sql_escape(created)}', '{sql_escape(synced)}') "
            "ON CONFLICT(server_id, mysql_id) DO UPDATE SET "
            "entry_type = excluded.entry_type, amount = excluded.amount, "
            "from_uuid = excluded.from_uuid, to_uuid = excluded.to_uuid, "
            "details = excluded.details, created_at = excluded.created_at, synced_at = excluded.synced_at"
        )
        if len(batch) >= 80:
            flush()
    flush()
    return count


def audit_mysql(conn) -> None:
    cur = conn.cursor()
    print("\nMySQL after normalize:")
    cur.execute(
        """
        SELECT details, COUNT(*) c, ROUND(SUM(amount), 2) total
        FROM root_treasury_ledger
        WHERE entry_type = 'TOWNY_SINK'
          AND DATE_FORMAT(CONVERT_TZ(created_at,'+00:00','-10:00'), '%Y-%m')
            = DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','-10:00'), '%Y-%m')
        GROUP BY details ORDER BY total DESC
        """
    )
    for row in cur.fetchall():
        print(f"  {row[0]}: {row[1]} rows, {row[2]} G")
    cur.execute(
        """
        SELECT ROUND(COALESCE(SUM(amount),0),2)
        FROM root_treasury_ledger
        WHERE entry_type = 'GRANT'
          AND DATE_FORMAT(CONVERT_TZ(created_at,'+00:00','-10:00'), '%Y-%m')
            = DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','-10:00'), '%Y-%m')
        """
    )
    print(f"  GRANT MTD: {cur.fetchone()[0]} G")


def audit_d1() -> None:
    print("\nD1 after resync:")
    rows = d1_exec(
        f"""
        SELECT details, COUNT(*) c, ROUND(SUM(amount), 2) total
        FROM rootmc_treasury_ledger
        WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'
          AND entry_type = 'TOWNY_SINK'
          AND strftime('%Y-%m', datetime(created_at, '-10 hours')) = strftime('%Y-%m', datetime('now', '-10 hours'))
        GROUP BY details ORDER BY total DESC
        """
    )
    for row in rows:
        print(f"  {row['details']}: {row['c']} rows, {row['total']} G")
    grant = d1_exec(
        f"""
        SELECT ROUND(COALESCE(SUM(amount),0),2) AS total
        FROM rootmc_treasury_ledger
        WHERE server_id = '{sql_escape(TREASURY_SERVER_ID)}'
          AND entry_type = 'GRANT'
          AND strftime('%Y-%m', datetime(created_at, '-10 hours')) = strftime('%Y-%m', datetime('now', '-10 hours'))
        """
    )
    print(f"  GRANT MTD: {grant[0]['total'] if grant else '?'} G")


def main() -> int:
    cfg = read_db_yaml()
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **cfg)
    try:
        print("Normalizing MySQL treasury ledger ...")
        mysql_stats = normalize_mysql(conn)
        print("MySQL:", mysql_stats)
        audit_mysql(conn)
    finally:
        conn.close()

    print("\nNormalizing D1 treasury ledger (direct) ...")
    d1_stats = normalize_d1_direct()
    print("D1 rows still above cap after direct normalize:", d1_stats)

    if HEARTBEAT_SERVER_ID != TREASURY_SERVER_ID:
        print(f"\nRemoving duplicate D1 treasury data for server_id={HEARTBEAT_SERVER_ID} ...")
        d1_exec(f"DELETE FROM rootmc_treasury_ledger WHERE server_id = '{sql_escape(HEARTBEAT_SERVER_ID)}'")
        d1_exec(f"DELETE FROM rootmc_treasury_sync_state WHERE server_id = '{sql_escape(HEARTBEAT_SERVER_ID)}'")
        d1_exec(f"DELETE FROM rootmc_treasury_balance_snapshots WHERE server_id = '{sql_escape(HEARTBEAT_SERVER_ID)}'")

    conn = pymysql.connect(charset="utf8mb4", **cfg)
    try:
        print("\nResyncing full MySQL ledger -> D1 ...")
        upserted = sync_mysql_ledger_to_d1(conn, replace=True)
        print(f"Upserted {upserted} row(s).")
        cur = conn.cursor()
        cur.execute("SELECT MAX(id) FROM root_treasury_ledger")
        last_id = int(cur.fetchone()[0] or 0)
        cur.execute("SELECT balance FROM root_economy_balances WHERE minecraft_uuid = %s LIMIT 1", (TOWNY_SERVER_UUID,))
        vault_row = cur.fetchone()
        vault = round(float(vault_row[0]), 2) if vault_row else 0.0
    finally:
        conn.close()

    synced = dt.datetime.now(dt.UTC).isoformat().replace("+00:00", "Z")
    d1_exec(
        "INSERT INTO rootmc_treasury_sync_state (server_id, last_ledger_mysql_id, updated_at, treasury_balance) "
        f"VALUES ('{sql_escape(TREASURY_SERVER_ID)}', {last_id}, '{sql_escape(synced)}', {vault}) "
        "ON CONFLICT(server_id) DO UPDATE SET "
        "treasury_balance = excluded.treasury_balance, "
        "last_ledger_mysql_id = MAX(rootmc_treasury_sync_state.last_ledger_mysql_id, excluded.last_ledger_mysql_id), "
        "updated_at = excluded.updated_at"
    )

    audit_d1()
    return 0


if __name__ == "__main__":
    sys.exit(main())
