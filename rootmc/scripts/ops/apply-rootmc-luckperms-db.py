#!/usr/bin/env python3
"""Apply RootMC LuckPerms groups to MySQL from luckperms-setup.commands.

Reads DB credentials from LuckPerms config.yml (never commit passwords to git).

Usage:
  python apply-rootmc-luckperms-db.py
  python apply-rootmc-luckperms-db.py --config "%USERPROFILE%/Desktop/RootMC/plugins/LuckPerms/config.yml"
  python apply-rootmc-luckperms-db.py --dry-run
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import pymysql

GROUPS = (
    "default",
    "wanderer",
    "settler",
    "pioneer",
    "citizen",
    "veteran",
    "elite",
    "champion",
    "supporter",
    "patron",
    "pro",
    "benefactor",
    "lifetime",
    "founder",
    "helper",
    "moderator",
    "admin",
    "developer",
    "owner",
)

TRACKS: list[tuple[str, list[str]]] = [
    (
        "player",
        ["default", "wanderer", "settler", "pioneer", "citizen", "veteran", "elite", "champion"],
    ),
    (
        "donor",
        ["default", "supporter", "patron", "pro", "benefactor", "lifetime", "founder"],
    ),
    ("staff", ["helper", "moderator", "admin", "developer", "owner"]),
]


def repo_commands_default() -> Path:
    return Path("/home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins/server/host-handoff/config-templates/luckperms-setup.commands")


def parse_commands_file(path: Path) -> list[tuple[str, str, int]]:
    """Return (group, permission, value) tuples for LuckPerms MySQL."""
    perms: list[tuple[str, str, int]] = []
    text = path.read_text(encoding="utf-8")
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("lp sync"):
            continue

        m = re.match(r"lp group (\w+) permission set (.+?) (true|false)\s*$", line)
        if m:
            perms.append((m.group(1), m.group(2), 1 if m.group(3) == "true" else 0))
            continue

        m = re.match(r"lp group (\w+) parent add (\w+)\s*$", line)
        if m:
            perms.append((m.group(1), f"group.{m.group(2)}", 1))
            continue

        m = re.match(r"lp group (\w+) setweight (\d+)\s*$", line)
        if m:
            perms.append((m.group(1), f"weight.{m.group(2)}", 1))
            continue

        m = re.match(r"lp group (\w+) setdisplayname (.+?)\s*$", line)
        if m:
            perms.append((m.group(1), f"displayname.{m.group(2)}", 1))
            continue

        m = re.match(r'lp group (\w+) meta setprefix (\d+) "(.+)"\s*$', line)
        if m:
            perms.append((m.group(1), f"prefix.{m.group(2)}.{m.group(3)}", 1))
            continue

    return perms


def load_mysql_config(config_path: Path) -> dict[str, str | int]:
    text = config_path.read_text(encoding="utf-8")
    host_port = re.search(r"^\s*address:\s*['\"]?([^'\"\n]+)", text, re.M)
    database = re.search(r"^\s*database:\s*['\"]?([^'\"\n]+)", text, re.M)
    username = re.search(r"^\s*username:\s*['\"]?([^'\"\n]+)", text, re.M)
    password = re.search(r"^\s*password:\s*['\"]?([^'\"\n]*)", text, re.M)
    if not all([host_port, database, username, password]):
        raise SystemExit(f"Could not parse MySQL settings from {config_path}")
    host_raw = host_port.group(1).strip()
    if ":" in host_raw:
        host, port_s = host_raw.rsplit(":", 1)
        port = int(port_s)
    else:
        host, port = host_raw, 3306
    return {
        "host": host,
        "port": port,
        "user": username.group(1).strip(),
        "password": password.group(1).strip(),
        "database": database.group(1).strip(),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    default_cfg = Path.home() / "Desktop" / "RootMC - Current" / "plugins" / "LuckPerms" / "config.yml"
    parser.add_argument("--config", type=Path, default=default_cfg)
    parser.add_argument("--commands", type=Path, default=repo_commands_default())
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.commands.is_file():
        raise SystemExit(f"Commands file not found: {args.commands}")
    if not args.config.is_file():
        raise SystemExit(f"LuckPerms config not found: {args.config}")

    perms = parse_commands_file(args.commands)
    if not perms:
        raise SystemExit(f"No permissions parsed from {args.commands}")

    print(f"Parsed {len(perms)} group permission rows from {args.commands.name}")
    for group in GROUPS:
        count = sum(1 for g, _, _ in perms if g == group)
        print(f"  {group}: {count}")

    if args.dry_run:
        print("Dry run — no database changes.")
        return

    cfg = load_mysql_config(args.config)
    conn = pymysql.connect(
        host=cfg["host"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
    )
    try:
        cur = conn.cursor()
        cur.execute("DELETE FROM luckperms_group_permissions")
        cur.execute("DELETE FROM luckperms_tracks")
        cur.execute("DELETE FROM luckperms_groups WHERE name = %s", ("member",))
        for g in GROUPS:
            cur.execute("INSERT IGNORE INTO luckperms_groups (name) VALUES (%s)", (g,))
        cur.executemany(
            "INSERT INTO luckperms_group_permissions "
            "(name, permission, value, server, world, expiry, contexts) "
            "VALUES (%s, %s, %s, 'global', 'global', 0, '{}')",
            [(g, p, v) for g, p, v in perms],
        )
        for track_name, track_groups in TRACKS:
            cur.execute(
                "INSERT INTO luckperms_tracks (name, `groups`) VALUES (%s, %s) "
                "ON DUPLICATE KEY UPDATE `groups` = VALUES(`groups`)",
                (track_name, json.dumps(track_groups)),
            )
        cur.execute(
            "DELETE FROM luckperms_user_permissions WHERE permission IN (%s, %s)",
            ("group.member", "group.player"),
        )
        conn.commit()
        print("Applied RootMC LuckPerms to MySQL:", cfg["database"])
        print("Groups:", len(GROUPS), "| tracks:", [t[0] for t in TRACKS])
        print("Run /lp sync on the server (or full restart) to reload from MySQL.")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
