"""Shim — runtime is ~/.ollama/skills/live-wx/scripts/live_wx.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "live-wx" / "scripts" / "live_wx.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"live-wx processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
