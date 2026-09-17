"""Shim — runtime is ~/.ollama/skills/report-blog/scripts/report_blog.py."""

from __future__ import annotations

from pathlib import Path

_PROCESSOR_FILE = Path.home() / ".ollama" / "skills" / "report-blog" / "scripts" / "report_blog.py"
if not _PROCESSOR_FILE.is_file():
    raise FileNotFoundError(f"report-blog processor is missing: {_PROCESSOR_FILE}")
exec(compile(_PROCESSOR_FILE.read_text(encoding="utf-8"), str(_PROCESSOR_FILE), "exec"), globals())
