"""Shim — runtime is ~/.ollama/skills/site-ops/scripts/site_ops.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "site-ops" / "scripts" / "site_ops.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"site-ops processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
