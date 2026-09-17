"""Shim — runtime is ~/.ollama/skills/telegram/scripts/telegram_rooms.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "telegram" / "scripts" / "telegram_rooms.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"telegram processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
