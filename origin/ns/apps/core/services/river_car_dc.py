"""Shim — runtime is ~/.ollama/skills/ecoflow-river-car/scripts/river_car_dc.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts" / "river_car_dc.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"River car DC processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
