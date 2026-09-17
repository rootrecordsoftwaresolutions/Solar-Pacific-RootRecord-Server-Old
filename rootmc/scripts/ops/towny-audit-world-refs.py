"""Find remaining Towny MySQL fields still referencing archive world name 'world'."""
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
cur.execute("SHOW TABLES")
tables = [r[0] for r in cur.fetchall() if r[0].upper().startswith("TOWNY_")]

needle = "world"
skip_prefixes = ("world_nether", "world_the_end", "world-old")

for table in sorted(tables):
    cur.execute(f"SHOW COLUMNS FROM `{table}`")
    cols = [r[0] for r in cur.fetchall()]
    text_cols = [c for c in cols if c.lower() not in ("uuid", "registered", "claimedat", "lastonline", "joinedtownat", "joinednationat", "movedhomeblockat", "ruinedtime", "forsaletime")]
    for col in text_cols:
        try:
            cur.execute(
                f"SELECT COUNT(*) FROM `{table}` WHERE `{col}` LIKE %s OR `{col}` LIKE %s",
                (f"{needle}#%", f"{needle},%"),
            )
            n = cur.fetchone()[0]
            if n:
                print(f"{table}.{col}: {n} row(s) with {needle}# or {needle},")
                cur.execute(
                    f"SELECT * FROM `{table}` WHERE `{col}` LIKE %s OR `{col}` LIKE %s LIMIT 5",
                    (f"{needle}#%", f"{needle},%"),
                )
                for row in cur.fetchall():
                    d = dict(zip(cols, row))
                    snippet = str(d.get(col, ""))[:120]
                    label = d.get("name") or d.get("uuid") or d.get("key") or "?"
                    print(f"  {label}: {snippet}")
        except Exception as exc:
            print(f"{table}.{col}: skip ({exc})")

conn.close()
