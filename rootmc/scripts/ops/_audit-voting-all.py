#!/usr/bin/env python3
"""Full voting metrics audit - MySQL + optional D1 via wrangler."""
import json
import re
import subprocess
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
DB_YAML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "Towny" / "settings" / "database.yml"
POST_RESET = "2026-07-01 00:00:00"


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


def d1_query(sql):
    api_dir = WORKSPACE / "Web Files" / "rootmc-api"
    ps = WORKSPACE / "scripts" / "load-rootmc-env.ps1"
    cmd = (
        f'powershell -NoProfile -Command ". \'{ps}\'; '
        f"Set-Location '{api_dir}'; "
        f"npx wrangler d1 execute rootmc --remote --json --command \"{sql.replace(chr(34), chr(92)+chr(34))}\""
        '"'
    )
    try:
        out = subprocess.check_output(cmd, shell=True, text=True, stderr=subprocess.STDOUT, timeout=60)
        data = json.loads(out)
        if isinstance(data, list) and data and "results" in data[0]:
            return data[0]["results"]
        return []
    except Exception as ex:
        return {"error": str(ex)}


conn = pymysql.connect(charset="utf8mb4", **read_db())
cur = conn.cursor()

print("=" * 60)
print("LISTING VOTE REWARDS (root_rewards_votes)")
print("=" * 60)

cur.execute("SHOW TABLES LIKE '%vote%'")
print("Vote tables:", [r[0] for r in cur.fetchall()])

cur.execute(
    """
    SELECT COUNT(*), COALESCE(SUM(gold_earned),0), COALESCE(AVG(gold_earned),0),
           MIN(voted_at), MAX(voted_at)
    FROM root_rewards_votes
    """
)
total_v, total_g, avg_g, min_v, max_v = cur.fetchone()
print(f"All-time: {total_v} votes, {float(total_g):.2f} G paid, avg {float(avg_g):.2f} G")
print(f"  Range: {min_v} -> {max_v}")

cur.execute(
    """
    SELECT COUNT(*), COALESCE(SUM(gold_earned),0)
    FROM root_rewards_votes WHERE voted_at >= %s
    """,
    (POST_RESET,),
)
july_v, july_g = cur.fetchone()
print(f"Post-Jul 1: {july_v} votes, {float(july_g):.2f} G")

cur.execute(
    """
    SELECT service, COUNT(*), ROUND(SUM(gold_earned),2), ROUND(AVG(gold_earned),2)
    FROM root_rewards_votes
    WHERE voted_at >= %s
    GROUP BY service ORDER BY COUNT(*) DESC
    """,
    (POST_RESET,),
)
print("\nBy service (post-reset):")
for row in cur.fetchall():
    print(f"  {row[0] or 'default':40} {row[1]:4} votes  {row[2]:8} G  avg {row[3]}")

cur.execute(
    """
    SELECT DATE(voted_at) d, COUNT(*), ROUND(SUM(gold_earned),2)
    FROM root_rewards_votes
    WHERE voted_at >= %s
    GROUP BY DATE(voted_at) ORDER BY d
    """,
    (POST_RESET,),
)
print("\nDaily (post-reset):")
for row in cur.fetchall():
    print(f"  {row[0]}  {row[1]:3} votes  {row[2]} G")

cur.execute(
    """
    SELECT uuid, COUNT(*), ROUND(SUM(gold_earned),2), MAX(voted_at)
    FROM root_rewards_votes
    WHERE voted_at >= %s
    GROUP BY uuid ORDER BY SUM(gold_earned) DESC LIMIT 10
    """,
    (POST_RESET,),
)
print("\nTop voters (post-reset):")
for row in cur.fetchall():
    print(f"  {row[0][:8]}...  {row[1]} votes  {row[2]} G  last {row[3]}")

print("\n" + "=" * 60)
print("TREASURY VOTE LEDGER")
print("=" * 60)
cur.execute(
    """
    SELECT COUNT(*), ROUND(SUM(amount),2), MIN(created_at), MAX(created_at)
    FROM root_treasury_ledger WHERE entry_type = 'VOTE'
    """
)
r = cur.fetchone()
print(f"All-time VOTE rows: {r[0]} rows, {r[1]} G outflow")

cur.execute(
    """
    SELECT COUNT(*), ROUND(SUM(amount),2)
    FROM root_treasury_ledger WHERE entry_type = 'VOTE' AND created_at >= %s
    """,
    (POST_RESET,),
)
r = cur.fetchone()
print(f"Post-Jul 1: {r[0]} rows, {r[1]} G")

cur.execute(
    """
    SELECT details, COUNT(*), ROUND(SUM(amount),2)
    FROM root_treasury_ledger
    WHERE entry_type = 'VOTE' AND created_at >= %s
    GROUP BY details ORDER BY SUM(amount) DESC LIMIT 15
    """,
    (POST_RESET,),
)
print("\nBy service (ledger details):")
for row in cur.fetchall():
    print(f"  {row[0] or '?':45} {row[1]:3}x  {row[2]} G")

# Gap check: rewards table gold vs ledger
cur.execute(
    "SELECT ROUND(SUM(gold_earned),2) FROM root_rewards_votes WHERE voted_at >= %s",
    (POST_RESET,),
)
rewards_sum = float(cur.fetchone()[0] or 0)
ledger_sum = float(r[1] if False else 0)
cur.execute(
    "SELECT ROUND(SUM(amount),2) FROM root_treasury_ledger WHERE entry_type='VOTE' AND created_at >= %s",
    (POST_RESET,),
)
ledger_sum = float(cur.fetchone()[0] or 0)
print(f"\nReconcile post-reset: rewards table {rewards_sum} G vs ledger VOTE {ledger_sum} G (gap {rewards_sum-ledger_sum:.2f})")

conn.close()

print("\n" + "=" * 60)
print("D1 - LISTING VOTES (governance multiplier)")
print("=" * 60)
d1_listing = d1_query(
    "SELECT COUNT(*) as c, COUNT(DISTINCT minecraft_uuid) as players, COUNT(DISTINCT service) as sites, MIN(voted_at) as first, MAX(voted_at) as last FROM rootmc_listing_votes"
)
print(d1_listing)

d1_recent = d1_query(
    "SELECT service, COUNT(*) as c FROM rootmc_listing_votes WHERE voted_at >= datetime('now', '-30 days') GROUP BY service ORDER BY c DESC"
)
print("\nD1 listing votes (30d):")
for row in d1_recent if isinstance(d1_recent, list) else []:
    print(f"  {row.get('service','?'):40} {row.get('c',0)}")

print("\n" + "=" * 60)
print("D1 - GOVERNANCE PROPOSAL VOTES")
print("=" * 60)
d1_prop = d1_query(
    "SELECT COUNT(*) as proposals FROM rootmc_community_proposals"
)
print("Proposals:", d1_prop)
d1_votes = d1_query(
    "SELECT vote, COUNT(*) as c, ROUND(SUM(vote_weight),2) as weighted FROM rootmc_community_proposal_votes GROUP BY vote"
)
print("Proposal votes:", d1_votes)

d1_open = d1_query(
    "SELECT id, title, status, vote_closes_at FROM rootmc_community_proposals ORDER BY created_at DESC LIMIT 5"
)
print("Recent proposals:", d1_open)
