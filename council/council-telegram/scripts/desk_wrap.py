"""Desk wrap: Cursor off, local Ollama, conclude threads."""
from __future__ import annotations

import json
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "desk-wrap.json"


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> None:
    PATH.parent.mkdir(parents=True, exist_ok=True)
    PATH.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def set_local() -> None:
    _save({"local": True, "cursor": "off"})


def is_local() -> bool:
    return bool(_load().get("local"))


def prompt_block() -> str:
    if not is_local():
        return ""
    return (
        "Code implementer is off for today. Stay on-device. "
        "Conclude open threads. Skip a new architecture saga. "
        "Finish your sentences."
    )
