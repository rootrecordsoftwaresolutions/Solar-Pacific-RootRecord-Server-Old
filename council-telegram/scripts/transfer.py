"""SHA-256 of council file transfers. Hash only — never file bytes in chat logs."""
from __future__ import annotations

import hashlib
import json
import time
from pathlib import Path

from .config import CONFIG_DIR

LOG = CONFIG_DIR / "transfer-log.jsonl"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        while True:
            chunk = f.read(1024 * 64)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def note(voice: str, path: Path, digest: str) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    row = {
        "ts": int(time.time()),
        "voice": voice,
        "name": Path(path).name,
        "sha256": digest,
        "bytes": Path(path).stat().st_size if Path(path).is_file() else 0,
    }
    with LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row) + "\n")


def caption_with_hash(caption: str, digest: str) -> str:
    tag = f"sha256={digest[:16]}…"
    cap = (caption or "").strip()
    if not cap:
        return tag
    room = 900 - len(tag) - 1
    if room < 40:
        return tag
    return f"{cap[:room]} {tag}"
