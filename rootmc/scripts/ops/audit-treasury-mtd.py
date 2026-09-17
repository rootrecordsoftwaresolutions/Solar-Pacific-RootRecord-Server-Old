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

cfg = dict(host=get("hostname"), port=int(get("port") or 3306), user=get("username"), password=get("password"), database=get("dbname"))
conn = pymysql.connect(charset="utf8mb4", **cfg)
cur = conn.cursor()

mtd = "DATE_FORMAT(CONVERT_TZ(created_at,'+00:00','-10:00'), '%Y-%m') = DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','-10:00'), '%Y-%m')"

print("=== MTD TOWNY_SINK by details ===")
cur.execute(f"SELECT details, COUNT(*), ROUND(SUM(amount),2) FROM root_treasury_ledger WHERE entry_type='TOWNY_SINK' AND {mtd} GROUP BY details ORDER BY SUM(amount) DESC")
for r in cur.fetchall(): print(r)

print("\n=== MTD GRANT rows ===")
cur.execute(f"SELECT id, amount, details, created_at FROM root_treasury_ledger WHERE entry_type='GRANT' AND {mtd} ORDER BY id")
for r in cur.fetchall(): print(r)
cur.execute(f"SELECT ROUND(SUM(amount),2) FROM root_treasury_ledger WHERE entry_type='GRANT' AND {mtd}")
print("GRANT MTD total:", cur.fetchone()[0])

print("\n=== ALL TIME ledger net ===")
cur.execute("""
SELECT ROUND(SUM(CASE WHEN entry_type IN ('GRANT','DIVIDEND','LOAN_DISBURSE','VOTE') THEN -amount ELSE amount END),2)
FROM root_treasury_ledger""")
print(cur.fetchone()[0])

print("\n=== Vault balance ===")
cur.execute("SELECT balance FROM root_economy_balances WHERE minecraft_uuid='a73f39b0-1b7c-2930-b4a3-ce101812d926'")
print(cur.fetchone()[0])

conn.close()
