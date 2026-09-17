"""Shim — runtime is ~/.ollama/skills/ecoflow-ac-solar-gate/scripts/ecoflow_ac_solar_gate.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "ecoflow-ac-solar-gate" / "scripts" / "ecoflow_ac_solar_gate.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"EcoFlow AC solar gate processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
