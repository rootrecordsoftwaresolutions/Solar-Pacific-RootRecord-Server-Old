"""Show root_homes and root_spawn rows still on overworld 'world'."""
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
for table in ("root_homes", "root_spawn"):
    cur.execute(f"SELECT * FROM `{table}` WHERE world_name = 'world'")
    cols = [d[0] for d in cur.description]
    rows = cur.fetchall()
    print(f"\n{table} ({len(rows)} on world):")
    for row in rows:
        print(dict(zip(cols, row)))
conn.close()
