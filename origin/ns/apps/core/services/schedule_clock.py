"""Shim — runtime is ~/.ollama/skills/scheduler-clock/scripts/schedule_clock.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "scheduler-clock" / "scripts" / "schedule_clock.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"scheduler-clock processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
