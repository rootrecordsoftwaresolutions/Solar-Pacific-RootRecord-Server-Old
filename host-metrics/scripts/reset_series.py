#!/usr/bin/env python3
"""Move EcoFlow/host/uptime jsonl aside so the next poll starts a new series.

Does not invent watts, SOC, or player counts. Leaves last-sample readers on
empty files until the next live write.
"""
from __future__ import annotations

import shutil
from datetime import datetime, timezone
from pathlib import Path

SKILLS = Path.home() / ".ollama" / "skills"
STAMP = datetime.now(timezone.utc).strftime("%Y%m%dT%H%MZ")


def _targets() -> list[Path]:
    rows: list[Path] = []
    hist = SKILLS / "database" / "store" / "ecoflow" / "history"
    if hist.is_dir():
        rows.extend(sorted(hist.glob("*.jsonl")))
    host = SKILLS / "host-metrics" / "store" / "host.jsonl"
    if host.is_file():
        rows.append(host)
    for name in ("uptime-events.jsonl", "host.jsonl"):
        p = SKILLS / "state" / "store" / name
        if p.is_file():
            rows.append(p)
    extra = SKILLS / "database" / "store" / "host.jsonl"
    if extra.is_file():
        rows.append(extra)
    return rows


def reset(dry_run: bool = False) -> list[str]:
    dest_root = SKILLS / "state" / "store" / f"series-bak-{STAMP}"
    moved: list[str] = []
    for src in _targets():
        dest = dest_root / src.name
        if src.parent.name == "history":
            dest = dest_root / "ecoflow-history" / src.name
        moved.append(f"{src} -> {dest}")
        if dry_run:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(src), str(dest))
    note = dest_root / "README.txt"
    if not dry_run:
        dest_root.mkdir(parents=True, exist_ok=True)
        note.write_text(
            f"Series moved {STAMP} UTC. Live writers create new jsonl on next sample.\n",
            encoding="utf-8",
        )
    return moved


if __name__ == "__main__":
    import sys

    dry = "--dry-run" in sys.argv
    for line in reset(dry_run=dry):
        print(("dry " if dry else "moved ") + line)
