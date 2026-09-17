"""Merged morning summary cron — delegates to morning-report.run_merged."""

from __future__ import annotations

import importlib.util
from pathlib import Path


def _morning():
    f = Path.home() / ".ollama" / "skills" / "morning-report" / "scripts" / "job.py"
    spec = importlib.util.spec_from_file_location("skill_morning_report", f)
    if spec is None or spec.loader is None:
        raise ImportError(str(f))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


async def run():
    await _morning().run_merged()
