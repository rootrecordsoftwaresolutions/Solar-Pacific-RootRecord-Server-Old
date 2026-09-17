"""Shim — runtime is ~/.ollama/skills/membership/scripts/membership.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "membership" / "scripts" / "membership.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"membership processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
