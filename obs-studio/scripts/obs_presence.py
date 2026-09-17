"""OBS must be an open process before any streaming/overlay automation runs.

AVA_ENABLE_OBS is the feature kill switch. Even when on, do nothing for
WebSocket, scene rotate, browser-source push, or audio SSE if obs64 is not
running — idle PC should not pay OBS tax.
"""
from __future__ import annotations

import logging
import time
from typing import Iterable

log = logging.getLogger("ava.obs_presence")

_OBS_EXE = frozenset({"obs64.exe", "obs32.exe", "obs.exe", "obs64", "obs32", "obs", "obs-studio"})
_cache_until = 0.0
_cache_val = False
_CACHE_S = 12.0


def _names_from_psutil() -> set[str]:
    try:
        import psutil
    except Exception:
        return set()
    found: set[str] = set()
    try:
        for proc in psutil.process_iter(["name"]):
            try:
                name = str((proc.info or {}).get("name") or "").strip().lower()
            except Exception:
                continue
            if name:
                found.add(name)
    except Exception:
        return set()
    return found


def _names_from_tasklist() -> set[str]:
    """Fallback when psutil is unavailable."""
    import subprocess

    try:
        out = subprocess.check_output(
            ["tasklist", "/FO", "CSV", "/NH"],
            text=True,
            errors="replace",
            timeout=4,
        )
    except Exception:
        return set()
    found: set[str] = set()
    for line in out.splitlines():
        # "obs64.exe","1234",...
        if not line.startswith('"'):
            continue
        name = line.split('"', 2)[1].strip().lower()
        if name:
            found.add(name)
    return found


def _any_obs(names: Iterable[str]) -> bool:
    for n in names:
        if n in _OBS_EXE or n.endswith("obs64.exe") or n.endswith("obs.exe"):
            return True
    return False


def _linux_obs_running() -> bool:
    import subprocess

    try:
        for args in (["pgrep", "-x", "obs"], ["pgrep", "-x", "obs-studio"]):
            r = subprocess.run(args, capture_output=True, timeout=3)
            if r.returncode == 0:
                return True
    except Exception:
        return False
    return False


def obs_process_running(*, force: bool = False) -> bool:
    """True if OBS Studio is open on this PC (cached ~12s)."""
    global _cache_until, _cache_val
    now = time.monotonic()
    if not force and now < _cache_until:
        return _cache_val
    names = _names_from_psutil()
    if not names:
        names = _names_from_tasklist()
    found = _any_obs(names)
    if not found:
        found = _linux_obs_running()
    _cache_val = found
    _cache_until = now + _CACHE_S
    return _cache_val


def obs_work_allowed() -> bool:
    """Feature flag on AND OBS process open AND not night-sleep."""
    from apps.core.services.feature_toggles import is_on

    if _night_sleeping():
        return False
    if not is_on("obs"):
        return False
    return obs_process_running()


def _night_sleeping() -> bool:
    from pathlib import Path
    import json

    q = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state" / "night-mode.json"
    if not q.is_file():
        return False
    try:
        raw = json.loads(q.read_text(encoding="utf-8"))
    except Exception:
        return False
    return bool(raw.get("sleeping") or raw.get("silent"))


def obs_skip_reason() -> str:
    from apps.core.services.feature_toggles import is_on

    if _night_sleeping():
        return "night_sleep"
    if not is_on("obs"):
        return "AVA_ENABLE_OBS=0"
    if not obs_process_running():
        return "obs_not_running"
    return ""
