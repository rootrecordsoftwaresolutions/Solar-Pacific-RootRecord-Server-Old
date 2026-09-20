"""Name-mention intake. Logs when a human says Ava/Bruce/Carly. Does not make bots speak."""
from __future__ import annotations

import json
import time
from typing import Any

from .config import CONFIG_DIR
from .router import NAME_RE

LOG = CONFIG_DIR / "name-intake.jsonl"


def voices_in(text: str) -> list[str]:
    found: list[str] = []
    t = text or ""
    for voice, rx in NAME_RE.items():
        if rx.search(t) and voice not in found:
            found.append(voice)
    return found


def note(*, text: str, from_id: Any, username: str, chat_id: Any) -> list[str]:
    voices = voices_in(text)
    if not voices:
        return []
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    row = {
        "ts": int(time.time()),
        "chat_id": str(chat_id),
        "from_id": from_id,
        "username": username,
        "voices": voices,
        "snippet": (text or "")[:200],
    }
    with LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")
    return voices
