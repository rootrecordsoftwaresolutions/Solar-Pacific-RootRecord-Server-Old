#!/usr/bin/env python3
"""Latest RootRecord AWS radar GIF(s) for Bruce to attach in council."""
from __future__ import annotations

from pathlib import Path

LIVE = (
    Path.home()
    / ".ollama"
    / "skills"
    / "rootrecord-aws"
    / "store"
    / "live"
    / "radar"
)
ARCHIVE = (
    Path.home()
    / ".ollama"
    / "skills"
    / "rootrecord-aws"
    / "store"
    / "archive"
)
CAPTION = (
    "Here's the latest radar and weather information for you. "
    "Let me know if theres anything else I can relay."
)


def latest_gifs(limit: int = 2) -> list[Path]:
    """Newest GIF under live/radar, else newest archived pack radar/."""
    gifs: list[Path] = []
    if LIVE.is_dir():
        gifs = sorted(
            (p for p in LIVE.glob("*.gif") if p.is_file() and p.stat().st_size > 0),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
    if not gifs and ARCHIVE.is_dir():
        gifs = sorted(
            (
                p
                for p in ARCHIVE.glob("*/radar/*.gif")
                if p.is_file() and p.stat().st_size > 0
            ),
            key=lambda p: p.stat().st_mtime,
            reverse=True,
        )
    return gifs[: max(1, limit)]


def main() -> int:
    paths = latest_gifs()
    if not paths:
        print(
            "No radar GIF on disk yet. "
            "Ingest writes them to rootrecord-aws/store/live/radar/ "
            "(from AWS work/radar Current.gif in each datapack)."
        )
        return 1
    print(f"RADAR_CAPTION={CAPTION}")
    for path in paths:
        print(f"RADAR_GIF={path.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
