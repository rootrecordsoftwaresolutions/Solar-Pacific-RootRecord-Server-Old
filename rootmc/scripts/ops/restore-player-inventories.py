#!/usr/bin/env python3
"""Merge player inventories from world-old/playerdata into world/playerdata.

Copies Inventory, EnderItems, SelectedItemSlot, and equipment (1.21+) from the
archive world while keeping each player's current position/health in the live world.

Usage:
  python restore-player-inventories.py --old path/to/world-old/playerdata --new path/to/world/playerdata
  python restore-player-inventories.py --old ... --new ... --dry-run
"""
from __future__ import annotations

import argparse
import gzip
import shutil
from pathlib import Path

import nbtlib
from nbtlib import File

# Tags that hold items / worn gear only (not position, XP, etc.)
INVENTORY_TAGS = (
    "Inventory",
    "EnderItems",
    "SelectedItemSlot",
    "equipment",
)


def load_dat(path: Path) -> File:
    with gzip.open(path, "rb") as fh:
        return nbtlib.load(fh)


def save_dat(nbt: File, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    nbt.save(path, gzipped=True)


def merge_player(old_path: Path, new_path: Path, backup_dir: Path | None) -> str:
    old = load_dat(old_path)
    uuid = old_path.stem

    if new_path.is_file():
        new = load_dat(new_path)
        action = "merged"
    else:
        new = File(old)
        action = "copied"

    copied = []
    for tag in INVENTORY_TAGS:
        if tag in old.root:
            new.root[tag] = old.root[tag]
            copied.append(tag)

    if not copied:
        return f"{uuid}: skip (no inventory tags in archive)"

    if backup_dir is not None and new_path.is_file():
        backup_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(new_path, backup_dir / new_path.name)

    save_dat(new, new_path)
    return f"{uuid}: {action} ({', '.join(copied)})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old", required=True, type=Path, help="world-old/playerdata directory")
    parser.add_argument("--new", required=True, type=Path, help="world/playerdata directory")
    parser.add_argument("--backup", type=Path, help="backup live playerdata before overwrite")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    old_dir: Path = args.old
    new_dir: Path = args.new
    if not old_dir.is_dir():
        raise SystemExit(f"Missing archive playerdata dir: {old_dir}")
    new_dir.mkdir(parents=True, exist_ok=True)

    files = sorted(old_dir.glob("*.dat"))
    if not files:
        raise SystemExit(f"No .dat files in {old_dir}")

    if args.dry_run:
        print(f"[dry-run] Would process {len(files)} player(s) from {old_dir} -> {new_dir}")
        for f in files[:10]:
            print(f"  {f.name}")
        if len(files) > 10:
            print(f"  ... +{len(files) - 10} more")
        return 0

    backup = args.backup
    if backup is None:
        backup = new_dir.parent / "playerdata-backup-before-restore"

    ok = 0
    for old_file in files:
        new_file = new_dir / old_file.name
        try:
            print(merge_player(old_file, new_file, backup))
            ok += 1
        except Exception as exc:
            print(f"{old_file.stem}: ERROR {exc}")

    print(f"Done. {ok}/{len(files)} player(s) restored. Backup: {backup}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
