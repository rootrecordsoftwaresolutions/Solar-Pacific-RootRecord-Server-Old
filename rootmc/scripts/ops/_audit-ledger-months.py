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

print("=== Ledger rows by month ===")
cur.execute(
    """
    SELECT DATE_FORMAT(created_at, '%%Y-%%m') AS ym, entry_type, COUNT(*) AS cnt, ROUND(SUM(amount),2) AS total
    FROM root_treasury_ledger
    GROUP BY ym, entry_type
    ORDER BY ym, entry_type
    """
)
for r in cur.fetchall():
    print(r)

cur.execute("SELECT COUNT(*) FROM root_treasury_ledger")
print("\nTotal rows:", cur.fetchone()[0])

cur.execute(
    """
    SELECT COUNT(*), ROUND(SUM(CASE WHEN entry_type IN ('TAX','DEATH','TOWNY_SINK','LOAN_PRINCIPAL','LOAN_INTEREST','OPENING') THEN amount ELSE 0 END),2),
           ROUND(SUM(CASE WHEN entry_type IN ('VOTE','GRANT','DIVIDEND','LOAN_DISBURSE') THEN amount ELSE 0 END),2)
    FROM root_treasury_ledger
    WHERE created_at >= '2026-06-01' AND created_at < '2026-07-01'
    """
)
print("June count/inflow/outflow-ish:", cur.fetchone())

cur.execute(
    """
    SELECT MIN(created_at), MAX(created_at), MIN(id), MAX(id)
    FROM root_treasury_ledger
    """
)
print("Date/id range:", cur.fetchone())

conn.close()
