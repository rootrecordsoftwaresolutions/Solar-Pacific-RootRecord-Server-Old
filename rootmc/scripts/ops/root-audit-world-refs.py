"""Check Root plugin tables for overworld refs still on 'world'."""
import re
from pathlib import Path

import pymysql

yaml = Path(
    r"D:\.1 Work Stations\RootMC\Server Handoffs\2. RootMC - Towny\plugins\Towny\settings\database.yml"
).read_text()


def g(key: str) -> str | None:
    m = re.search(rf"^\s*{key}:\s*'?([^'\r\n]+)'?\s*$", yaml, re.M)
    return m.group(1).strip() if m else None


conn = pymysql.connect(
    host=g("hostname"),
    port=int(g("port") or 3306),
    user=g("username"),
    password=g("password"),
    database=g("dbname"),
    charset="utf8mb4",
)
cur = conn.cursor()
for table in ("root_homes", "root_spawn", "root_warps"):
    cur.execute("SHOW TABLES LIKE %s", (table,))
    if not cur.fetchone():
        continue
    cur.execute(f"SHOW COLUMNS FROM `{table}`")
    cols = [r[0] for r in cur.fetchall()]
    world_cols = [c for c in cols if "world" in c.lower()]
    for col in world_cols:
        cur.execute(f"SELECT `{col}`, COUNT(*) c FROM `{table}` GROUP BY `{col}`")
        rows = cur.fetchall()
        if rows:
            print(table, col, rows)
conn.close()
