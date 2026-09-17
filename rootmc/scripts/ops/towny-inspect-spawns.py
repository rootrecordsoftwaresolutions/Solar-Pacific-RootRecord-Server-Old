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
cur.execute("SELECT name, homeblock, spawn, outpostSpawns FROM TOWNY_TOWNS")
for name, home, spawn, outposts in cur.fetchall():
    print(f"{name} | home={home!r} | spawn={spawn!r}")
cur.execute("SELECT world, COUNT(*) FROM TOWNY_TOWNBLOCKS GROUP BY world")
print("townblocks:", cur.fetchall())
conn.close()
