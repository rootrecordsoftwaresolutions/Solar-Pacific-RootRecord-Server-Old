#!/usr/bin/env python3
"""Mark map return grant as already claimed (e.g. town resettlement equivalent)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

import pymysql

WORKSPACE = Path("/home/rootrecord/.ollama/skills/origin/workstations")
ROOTMC_YML = WORKSPACE / "Server Handoffs\2. RootMC - Towny" / "plugins" / "RootRecord" / "rootmc.yml"

PRE_CLAIMED = (
    ("3e660994-b16c-4714-bc15-9081aa928729", "Alexrs94", 1000.0),
    ("d24c48ed-a6af-4827-bd37-c868be39a50d", "ZuppaFredda", 1000.0),
)


def read_mysql_from_rootmc_yml() -> dict:
    text = ROOTMC_YML.read_text(encoding="utf-8")

    def get(key: str) -> str:
        m = re.search(rf"^\s*{re.escape(key)}:\s*'?([^'\r\n#]+)'?\s*", text, re.M)
        if not m:
            raise RuntimeError(f"Missing mysql.{key} in {ROOTMC_YML}")
        return m.group(1).strip()

    return {
        "host": get("host"),
        "port": int(get("port") or 3306),
        "user": get("username"),
        "password": get("password"),
        "database": get("database"),
        "table_prefix": get("table-prefix") if "table-prefix" in text else "root_",
    }


def main() -> int:
    cfg = read_mysql_from_rootmc_yml()
    table = cfg.pop("table_prefix") + "map_return_grants"
    conn = pymysql.connect(**cfg)
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {table} (
                  minecraft_uuid CHAR(36) PRIMARY KEY,
                  amount DOUBLE NOT NULL,
                  claimed_at DATETIME NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
            )
            for uuid, name, amount in PRE_CLAIMED:
                cur.execute(f"SELECT 1 FROM {table} WHERE minecraft_uuid = %s LIMIT 1", (uuid,))
                if cur.fetchone():
                    print(f"skip (already claimed): {name} ({uuid})")
                    continue
                cur.execute(
                    f"INSERT INTO {table} (minecraft_uuid, amount, claimed_at) VALUES (%s, %s, NOW())",
                    (uuid, amount),
                )
                print(f"marked pre-claimed: {name} ({uuid}) â€” {amount} G (town-resettlement)")
        conn.commit()
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
