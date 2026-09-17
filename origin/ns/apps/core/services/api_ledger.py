"""Shim — runtime is ~/.ollama/skills/api-prices/scripts/api_ledger.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "api-prices" / "scripts" / "api_ledger.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"api-prices processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
