"""Finish world rename: Moreni homeblock, root_homes, root_spawn."""
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
    autocommit=False,
)
cur = conn.cursor()

# Moreni: no claimed plots â€” derive homeblock from spawn block coords
cur.execute("SELECT spawn FROM TOWNY_TOWNS WHERE name = %s", ("Moreni",))
spawn = cur.fetchone()
if spawn and spawn[0]:
    parts = spawn[0].split("#")
    if len(parts) >= 4 and parts[0] == "world-old":
        bx = int(float(parts[1]))
        bz = int(float(parts[3]))
        home = f"world-old#{bx // 16}#{bz // 16}"
        cur.execute(
            "UPDATE TOWNY_TOWNS SET homeblock = %s WHERE name = %s AND (homeblock IS NULL OR homeblock = '')",
            (home, "Moreni"),
        )
        print(f"Moreni homeblock: {cur.rowcount} -> {home}")
    else:
        print("Moreni: unexpected spawn format, skipped homeblock")
else:
    cur.execute(
        "SELECT x, z FROM TOWNY_TOWNBLOCKS WHERE town = %s ORDER BY claimedAt LIMIT 1",
        ("Moreni",),
    )
    row = cur.fetchone()
    if row:
        home = f"world-old#{row[0]}#{row[1]}"
        cur.execute(
            "UPDATE TOWNY_TOWNS SET homeblock = %s WHERE name = %s AND (homeblock IS NULL OR homeblock = '')",
            (home, "Moreni"),
        )
        print(f"Moreni homeblock: {cur.rowcount} -> {home}")
    else:
        print("Moreni: no townblocks found, skipped homeblock")

for table in ("root_homes", "root_spawn"):
    cur.execute(f"UPDATE `{table}` SET world_name = %s WHERE world_name = %s", ("world-old", "world"))
    print(f"{table}: {cur.rowcount} row(s) world -> world-old")

conn.commit()
conn.close()
print("Done.")
