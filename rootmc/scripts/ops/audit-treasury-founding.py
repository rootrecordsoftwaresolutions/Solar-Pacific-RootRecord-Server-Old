#!/usr/bin/env python3
import re
from pathlib import Path
import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
text = (WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml").read_text()

def get(k):
    m = re.search(rf"^\s*{re.escape(k)}:\s*'?([^'\r\n]+)'?\s*$", text, re.M)
    return m.group(1).strip()

conn = pymysql.connect(
    host=get("hostname"), port=int(get("port") or 3306), user=get("username"),
    password=get("password"), database=get("dbname"), charset="utf8mb4",
)
cur = conn.cursor()

print("=== Founding TOWNY_SINK rows ===")
cur.execute(
    """
    SELECT id, amount, details, created_at
    FROM root_treasury_ledger
    WHERE entry_type='TOWNY_SINK'
      AND (
        details LIKE 'towny:new-town%'
        OR details LIKE 'towny:new-nation%'
        OR details LIKE 'backfill:towny:new-town%'
        OR details LIKE 'backfill:towny:new-nation%'
      )
    ORDER BY created_at, id
    """
)
for r in cur.fetchall():
    print(r)

print("\n=== Founding GRANT rows ===")
cur.execute(
    """
    SELECT id, amount, details, created_at
    FROM root_treasury_ledger
    WHERE entry_type='GRANT'
      AND details LIKE 'backfill:grant:%'
    ORDER BY created_at, id
    """
)
for r in cur.fetchall():
    print(r)

print("\n=== July towny:new-town live rows ===")
cur.execute(
    """
    SELECT id, amount, details, from_uuid, to_uuid, created_at
    FROM root_treasury_ledger
    WHERE entry_type='TOWNY_SINK' AND details='towny:new-town'
      AND created_at >= '2026-07-01'
    ORDER BY id
    """
)
for r in cur.fetchall():
    print(r)

conn.close()
