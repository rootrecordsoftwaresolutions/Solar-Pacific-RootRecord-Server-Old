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
cur.execute("SELECT name, registered, FROM_UNIXTIME(registered/1000) FROM TOWNY_TOWNS ORDER BY registered")
print("Current towns:")
for r in cur.fetchall():
    print(r)
cur.execute(
    "SELECT COUNT(*) FROM root_rewards_votes WHERE voted_at >= %s AND voted_at < %s",
    ("2026-06-01 10:00:00", "2026-07-01 10:00:00"),
)
print("June HST votes:", cur.fetchone()[0])
cur.execute(
    "SELECT entry_type, COUNT(*), ROUND(SUM(amount),2) FROM root_treasury_ledger GROUP BY entry_type"
)
print("Current ledger:")
for r in cur.fetchall():
    print(r)
conn.close()
