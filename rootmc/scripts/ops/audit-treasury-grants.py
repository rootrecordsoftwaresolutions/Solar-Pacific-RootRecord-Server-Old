#!/usr/bin/env python3
import re
from pathlib import Path
import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
text = DB_YAML.read_text(encoding="utf-8")

def get(key):
    m = re.search(rf"^\s*{re.escape(key)}:\s*'?([^'\r\n]+)'?\s*$", text, re.M)
    return m.group(1).strip()

cfg = dict(
    host=get("hostname"),
    port=int(get("port") or 3306),
    user=get("username"),
    password=get("password"),
    database=get("dbname"),
)
conn = pymysql.connect(charset="utf8mb4", **cfg)
cur = conn.cursor()

print("=== GRANT rows ===")
cur.execute(
    "SELECT id, amount, details, created_at FROM root_treasury_ledger WHERE entry_type='GRANT' ORDER BY id"
)
for r in cur.fetchall():
    print(r)

print("\n=== TOWNY_SINK claim/bonus rows ===")
cur.execute(
    """
    SELECT id, amount, details, created_at
    FROM root_treasury_ledger
    WHERE entry_type='TOWNY_SINK'
      AND (
        details LIKE 'towny:claim%'
        OR details LIKE 'backfill:towny:claim%'
        OR details LIKE 'backfill:towny:bonus-block%'
      )
    ORDER BY id
    """
)
claim_rows = cur.fetchall()
for r in claim_rows:
    print(r)

claim_total = sum(float(r[1]) for r in claim_rows)
print(f"\nClaim+bonus total: {claim_total} G")

cur.execute(
    """
    SELECT ROUND(SUM(amount),2)
    FROM root_treasury_ledger
    WHERE entry_type='GRANT'
    """
)
print(f"GRANT total: {cur.fetchone()[0]} G")

cur.execute(
    """
    SELECT ROUND(SUM(CASE
      WHEN entry_type IN ('GRANT','DIVIDEND','LOAN_DISBURSE','VOTE') THEN -amount
      ELSE amount END), 2)
    FROM root_treasury_ledger
    """
)
print(f"LEDGER NET: {cur.fetchone()[0]} G")
conn.close()
