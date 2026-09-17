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
    SELECT DATE_FORMAT(CONVERT_TZ(created_at,'+00:00','-10:00'), '%Y-%m') AS hst_month,
           entry_type, COUNT(*), ROUND(SUM(amount),2)
    FROM root_treasury_ledger
    GROUP BY hst_month, entry_type
    ORDER BY hst_month, entry_type
    """
)
print("By HST month:")
for r in cur.fetchall():
    print(r)
cur.execute("SELECT MIN(id), MAX(id), MIN(created_at), MAX(created_at) FROM root_treasury_ledger")
print("Range:", cur.fetchone())
conn.close()
