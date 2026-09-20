"""Scheduler entry for radar-archive."""
from __future__ import annotations

import importlib.util
from pathlib import Path

_PROC = Path(__file__).resolve().parent / "radar_archive.py"


def _mod():
    spec = importlib.util.spec_from_file_location("skill_radar_archive", _PROC)
    if spec is None or spec.loader is None:
        raise ImportError(str(_PROC))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


async def run(reason: str = "poll"):
    return _mod().run()
