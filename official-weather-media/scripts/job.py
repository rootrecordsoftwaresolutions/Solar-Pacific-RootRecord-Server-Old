"""Scheduler entry for official-weather-media."""
from __future__ import annotations

import asyncio
import importlib.util
from pathlib import Path

_PROC = Path(__file__).resolve().parent / "official_weather_media.py"


def _mod():
    spec = importlib.util.spec_from_file_location("skill_official_weather_media", _PROC)
    if spec is None or spec.loader is None:
        raise ImportError(str(_PROC))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


async def run(reason: str = "poll"):
    m = _mod()
    result = await asyncio.to_thread(m.run)
    if result.get("ok"):
        try:
            result["obs"] = await m.apply_obs_scenes()
        except Exception as exc:
            result["obs"] = {"ok": False, "detail": str(exc)[:240]}
    return result
