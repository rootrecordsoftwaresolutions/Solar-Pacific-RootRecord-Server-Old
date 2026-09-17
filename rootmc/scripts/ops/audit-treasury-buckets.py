#!/usr/bin/env python3
import re
from pathlib import Path
import pymysql

text = Path(r"D:\.1 Work Stations\RootMC\Server Handoffs\2. RootMC - Towny\plugins\Towny\settings\database.yml").read_text()

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
    SELECT
      CASE
        WHEN details LIKE 'backfill:%%' THEN 'backfill'
        WHEN created_at >= '2026-07-01' THEN 'july_live'
        ELSE 'other'
      END AS bucket,
      entry_type,
      COUNT(*),
      ROUND(SUM(amount), 2)
    FROM root_treasury_ledger
    WHERE entry_type IN ('GRANT', 'TOWNY_SINK')
    GROUP BY bucket, entry_type
    ORDER BY bucket, entry_type
    """
)
for r in cur.fetchall():
    print(r)
conn.close()
