#!/usr/bin/env python3
import os, subprocess, json, sys
from pathlib import Path

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
WRANGLER_DIR = WORKSPACE / "Web Files" / "rootmc-api"
WRANGLER_DB = "rootmc"

def d1_query(sql: str):
    sql_one_line = " ".join(sql.split())
    cmd = (
        f'npx wrangler d1 execute {WRANGLER_DB} --remote --json '
        f'--command "{sql_one_line.replace(chr(34), chr(92) + chr(34))}"'
    )
    res = subprocess.run(cmd, cwd=WRANGLER_DIR, env=os.environ.copy(), check=True, capture_output=True, text=True, shell=True)
    payload = json.loads(res.stdout)
    return payload[0].get("results", [])

if __name__ == "__main__":
    print("D1 ledger by month:")
    for row in d1_query(
        "SELECT strftime('%Y-%m', datetime(created_at, '-10 hours')) AS m, entry_type, COUNT(*) c, ROUND(SUM(amount),2) t "
        "FROM rootmc_treasury_ledger WHERE server_id='rootmc' GROUP BY m, entry_type ORDER BY m, entry_type"
    ):
        print(row)
    print("\nJune row sample:")
    for row in d1_query(
        "SELECT mysql_id, entry_type, amount, substr(details,1,50), created_at FROM rootmc_treasury_ledger "
        "WHERE server_id='rootmc' AND datetime(created_at,'-10 hours') >= '2026-06-01' "
        "AND datetime(created_at,'-10 hours') < '2026-07-01' LIMIT 5"
    ):
        print(row)
