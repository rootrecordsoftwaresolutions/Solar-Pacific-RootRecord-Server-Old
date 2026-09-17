#!/usr/bin/env python3
import json
import re
import urllib.request
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"


def read_db():
    text = DB_YAML.read_text(encoding="utf-8")

    def get(k):
        m = re.search(rf"^\s*{re.escape(k)}:\s*'?([^\r\n']+)'?\s*$", text, re.M)
        return m.group(1).strip()

    return dict(
        host=get("hostname"),
        port=int(get("port") or 3306),
        user=get("username"),
        password=get("password"),
        database=get("dbname"),
    )


conn = pymysql.connect(charset="utf8mb4", **read_db())
cur = conn.cursor()

uuid = "d24c48ed-a6af-4827-bd37-c868be39a50d"
cur.execute(
    "SELECT id, amount, details, created_at FROM root_treasury_ledger "
    "WHERE entry_type='VOTE' AND created_at BETWEEN '2026-07-01 08:50' AND '2026-07-01 09:30'"
)
print("VOTE ledger Jul 1 08:50-09:30:", cur.fetchall())

cur.execute(
    "SELECT MIN(created_at), MAX(created_at), COUNT(*) FROM root_treasury_ledger WHERE entry_type='VOTE'"
)
print("VOTE ledger range:", cur.fetchone())

cur.execute(
    """
    SELECT v.id, v.gold_earned, v.service, v.voted_at
    FROM root_rewards_votes v
    WHERE v.uuid = %s AND v.voted_at >= '2026-07-01 00:00:00'
    ORDER BY v.voted_at
    """,
    (uuid,),
)
print(f"\nAll Jul votes for {uuid[:8]}...:")
for row in cur.fetchall():
    print(" ", row)

cur.execute("SHOW TABLES LIKE '%name%'")
tables = [r[0] for r in cur.fetchall()]

def player_name(uid):
    for tbl, col in [
        ("root_essentials_users", "uuid"),
        ("towny_residents", "uuid"),
        ("root_rewards_vote_totals", "uuid"),
    ]:
        if tbl not in tables and tbl != "root_rewards_vote_totals":
            continue
        try:
            if tbl == "root_rewards_vote_totals":
                cur.execute(f"SELECT username FROM {tbl} WHERE uuid=%s LIMIT 1", (uid,))
            else:
                cur.execute(f"SELECT name FROM {tbl} WHERE {col}=%s LIMIT 1", (uid,))
            r = cur.fetchone()
            if r and r[0]:
                return r[0]
        except Exception:
            pass
    return "?"

cur.execute(
    """
    SELECT uuid, COUNT(*), ROUND(SUM(gold_earned),2)
    FROM root_rewards_votes WHERE voted_at >= '2026-07-01'
    GROUP BY uuid ORDER BY SUM(gold_earned) DESC LIMIT 10
    """
)
print("\nTop voters (post-reset) with names:")
for uid, cnt, gold in cur.fetchall():
    print(f"  {player_name(uid):16} {uid[:8]}...  {cnt:3} votes  {gold} G")

# Site bonus coverage
SITE_BONUSES = [
    ("minecraft-?mp", 1.05),
    ("minecraftservers\\.org", 1.05),
    ("minecraft-server-list", 1.05),
    ("minecraft\\.buzz", 1.08),
    ("topminecraftservers", 1.05),
    ("minerank", 1.1),
    ("minecraftlist\\.org", 1.05),
    ("planet\\s*minecraft|planetminecraft", 1.12),
]

def bonus(svc):
    for pat, b in SITE_BONUSES:
        if re.search(pat, svc, re.I):
            return b
    return 1.0

cur.execute(
    "SELECT DISTINCT service FROM root_rewards_votes WHERE voted_at >= '2026-07-01' ORDER BY service"
)
print("\nGovernance site bonus mapping (post-reset services):")
for (svc,) in cur.fetchall():
    b = bonus(svc)
    flag = "OK" if b > 1 else "MISS â€” no constitution bonus"
    print(f"  {svc:30} bonus {b}  [{flag}]")

conn.close()

print("\nGovernance API:")
try:
    with urllib.request.urlopen(
        "https://api.rootmc.net/api/rootstat/minecraft/governance/voting-power?server_id=rootmc",
        timeout=30,
    ) as resp:
        data = json.load(resp)
    print(f"  Eligible voters: {data.get('eligible_count')}  total_raw: {data.get('total_raw')}")
    for row in sorted(data.get("rows") or [], key=lambda r: -(r.get("share_percent") or 0))[:12]:
        print(
            f"  {row.get('minecraft_username') or '?':16} "
            f"{row.get('share_percent', 0):6.2f}%  "
            f"playtime {row.get('playtime_seconds', 0)//3600}h  "
            f"NW {row.get('net_worth', 0):.0f}G  "
            f"mult {row.get('site_multiplier', 1)}  "
            f"sites {row.get('sites_voted')}"
        )
except Exception as ex:
    print("  ERROR:", ex)
