"""Shim — runtime is ~/.ollama/skills/council-telegram/scripts/council-skills/radar-gif/run.py."""
from __future__ import annotations

import runpy
from pathlib import Path

_PROCESSOR_FILE = (
    Path.home()
    / ".ollama"
    / "skills"
    / "council-telegram"
    / "scripts"
    / "council-skills"
    / "radar-gif"
    / "run.py"
)
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"radar-gif runner missing: {_PROCESSOR_FILE}")
runpy.run_path(str(_PROCESSOR_FILE), run_name="__main__")
