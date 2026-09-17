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

cur.execute(
    """
    SELECT COUNT(*), ROUND(SUM(amount),2)
    FROM root_treasury_ledger
    WHERE entry_type='TOWNY_SINK'
      AND (
        details LIKE 'towny:claim%'
        OR details LIKE 'backfill:towny:claim%'
        OR details LIKE 'backfill:towny:bonus-block%'
      )
    """
)
sink = cur.fetchone()
print("Claim+bonus sinks:", sink)

cur.execute(
    """
    SELECT COUNT(*), ROUND(SUM(amount),2)
    FROM root_treasury_ledger
    WHERE entry_type='GRANT'
      AND (
        details LIKE 'normalize:grant:claim:%'
        OR details LIKE 'normalize:grant:bonus:%'
        OR (amount IN (12, 16) AND details LIKE 'operator=%')
      )
    """
)
print("Claim-sized grants:", cur.fetchone())

cur.execute(
    """
    SELECT id, amount, from_uuid, to_uuid, details
    FROM root_treasury_ledger
    WHERE entry_type='TOWNY_SINK' AND details='towny:claim'
    ORDER BY id LIMIT 5
    """
)
print("\nSample live claims:")
for r in cur.fetchall():
    print(r)

conn.close()
