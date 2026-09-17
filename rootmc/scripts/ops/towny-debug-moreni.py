import re
from pathlib import Path
import pymysql
yaml = Path(r"D:\.1 Work Stations\RootMC\Server Handoffs\2. RootMC - Towny\plugins\Towny\settings\database.yml").read_text()
g=lambda k: re.search(rf"^\s*{k}:\s*'?([^'\r\n]+)'?\s*$",yaml,re.M).group(1).strip()
c=pymysql.connect(host=g("hostname"),port=int(g("port")),user=g("username"),password=g("password"),database=g("dbname"),charset="utf8mb4")
cur=c.cursor()
cur.execute("SELECT name, nation, homeblock, spawn FROM TOWNY_TOWNS WHERE name='Moreni'")
print("town", cur.fetchall())
cur.execute("SELECT name, nationSpawn FROM TOWNY_NATIONS WHERE name='Althaea'")
print("nation", cur.fetchall())
cur.execute("SELECT town, name, world, x, z FROM TOWNY_TOWNBLOCKS LIMIT 5")
print("sample blocks", cur.fetchall())
cur.execute("SELECT DISTINCT town FROM TOWNY_TOWNBLOCKS")
print("towns with blocks", cur.fetchall())
c.close()
