#!/usr/bin/env python3
"""Quick ledger donation totals from live MySQL."""
import re
from pathlib import Path

import pymysql

ROOT = Path("/home/rootrecord/.ollama/skills/origin/workstations")
db_yml = ROOT / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
text = db_yml.read_text(encoding="utf-8")


def get(k: str) -> str:
    m = re.search(rf"^\s*{re.escape(k)}:\s*'?(?P<v>[^\r\n']+)'?\s*$", text, re.M)
    return m.group("v").strip() if m else ""


cfg = {
    "host": get("hostname"),
    "port": int(get("port") or 3306),
    "user": get("username"),
    "password": get("password"),
    "database": get("dbname"),
}
conn = pymysql.connect(charset="utf8mb4", **cfg)
cur = conn.cursor()
cur.execute(
    "SELECT COALESCE(SUM(amount), 0) FROM root_treasury_ledger WHERE entry_type = 'DONATION'"
)
total = float(cur.fetchone()[0])
cur.execute(
    "SELECT created_at, from_uuid, amount, details FROM root_treasury_ledger "
    "WHERE entry_type = 'DONATION' ORDER BY id DESC LIMIT 10"
)
rows = cur.fetchall()
print("donation_total_g", round(total, 2))
for r in rows:
    print(r)
conn.close()
