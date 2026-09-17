"""Shim — runtime is ~/.ollama/skills/official-weather-media/scripts/official_weather_media.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "official-weather-media" / "scripts" / "official_weather_media.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"Official weather media processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
