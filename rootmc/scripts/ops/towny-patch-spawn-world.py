"""Patch Towny TOWNS homeblock/spawn from world# -> world-old# (hash format)."""
import re
from pathlib import Path

import pymysql

yaml = Path(
    r"D:\.1 Work Stations\RootMC\Server Handoffs\2. RootMC - Towny\plugins\Towny\settings\database.yml"
).read_text()


def g(key: str) -> str | None:
    m = re.search(rf"^\s*{key}:\s*'?([^'\r\n]+)'?\s*$", yaml, re.M)
    return m.group(1).strip() if m else None


src, dst = "world", "world-old"
conn = pymysql.connect(
    host=g("hostname"),
    port=int(g("port") or 3306),
    user=g("username"),
    password=g("password"),
    database=g("dbname"),
    charset="utf8mb4",
    autocommit=False,
)
cur = conn.cursor()
for col in ("homeblock", "spawn", "outpostSpawns"):
    cur.execute(f"SHOW COLUMNS FROM TOWNY_TOWNS LIKE %s", (col,))
    if not cur.fetchone():
        continue
    cur.execute(
        f"UPDATE TOWNY_TOWNS SET `{col}`=REPLACE(`{col}`, %s, %s) "
        f"WHERE `{col}` LIKE CONCAT(%s, '#%%')",
        (src + "#", dst + "#", src),
    )
    print(f"{col}: {cur.rowcount} row(s)")
conn.commit()
conn.close()
print("Done.")
