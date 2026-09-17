"""Patch all remaining Towny hash-format world refs: world# -> world-old#."""
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

patches = [
    ("TOWNY_TOWNS", ("homeblock", "spawn", "outpostSpawns")),
    ("TOWNY_NATIONS", ("nationSpawn",)),
    ("TOWNY_JAILS", ("townBlock", "spawns")),
    ("TOWNY_RESIDENTS", ("metadata",)),
    ("TOWNY_PLOTGROUPS", ("metadata",)),
    ("TOWNY_DISTRICTS", ("metadata",)),
]

total = 0
for table, cols in patches:
    cur.execute("SHOW TABLES LIKE %s", (table,))
    if not cur.fetchone():
        continue
    cur.execute(f"SHOW COLUMNS FROM `{table}`")
    existing = {r[0] for r in cur.fetchall()}
    for col in cols:
        if col not in existing:
            continue
        cur.execute(
            f"UPDATE `{table}` SET `{col}`=REPLACE(`{col}`, %s, %s) "
            f"WHERE `{col}` LIKE CONCAT(%s, '#%%') OR `{col}` LIKE CONCAT(%s, ',%%')",
            (src + "#", dst + "#", src, src),
        )
        if cur.rowcount:
            print(f"{table}.{col}: {cur.rowcount}")
            total += cur.rowcount

conn.commit()
conn.close()
print(f"Done. {total} field(s) patched.")
