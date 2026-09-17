"""Timed Cursor self-repair window. Council reads; Cursor implements.

Default window 30 minutes. Cap 30 Cursor launches per rolling hour.
Origin recycle is separate and rarer. Never prints secrets.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR, load_config

PATH = CONFIG_DIR / "self-repair.json"
LOG_PATH = Path.home() / ".ollama" / "skills" / "logs" / "store" / "self-repair.log"
REPO = Path(__file__).resolve().parents[2]
WINDOW_S = 30 * 60
MAX_PER_HOUR = 30
MAX_RECYCLE_PER_HOUR = 3
HOUR_S = 3600

FIRST_PROMPT = """You are on the live AVA-CORE tree. Alexander opened a 30-minute self-repair window. Council may request more implementer jobs (cap 30/hour). You implement. Ava/Bruce/Carly do not write production code.

Workspace: /home/rootrecord/.ollama/skills/origin

Do not read or print .env, secrets.env, credentials, tokens, or API keys.
Live numbers only from a check you just ran.
Do not reboot the whole PC. Origin recycle is scripts/recycle-origin.sh only if the :8787 process is wedged.

Inspect remaining desk bugs and implement real fixes:
- morning / summary reports after noon HST
- stale dated report files (not today) posting to Telegram
- Hawaiian glossary false-firing on clock/place English
- personas addressing themselves
- OBS rotator must stay off until Ava Ops toggles it
- council self-repair window + origin recycle loop in launch.sh

Run the tests you can with .venv/bin/python3. Keep the change small. English replies in any Telegram copy.
"""


def _now() -> int:
    return int(time.time())


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> dict[str, Any]:
    PATH.parent.mkdir(parents=True, exist_ok=True)
    out = dict(data)
    out["updated"] = _now()
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(out, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)
    return out


def _prune(times: list[Any], window: int = HOUR_S, now: int | None = None) -> list[int]:
    now = now or _now()
    out: list[int] = []
    for raw in times or []:
        try:
            ts = int(raw)
        except (TypeError, ValueError):
            continue
        if now - ts < window:
            out.append(ts)
    return out


def snapshot() -> dict[str, Any]:
    data = _load()
    now = _now()
    until = int(data.get("until") or 0)
    launches = _prune(data.get("launches") or [], now=now)
    recycles = _prune(data.get("recycles") or [], now=now)
    max_h = int(data.get("max_per_hour") or MAX_PER_HOUR)
    alive = until > now
    pid = int(data.get("pid") or 0)
    running = bool(pid) and _pid_alive(pid)
    return {
        "ok": True,
        "active": alive,
        "until": until,
        "remaining_s": max(0, until - now) if alive else 0,
        "max_per_hour": max_h,
        "launches_this_hour": len(launches),
        "recycles_this_hour": len(recycles),
        "job_running": running,
        "last_status": str(data.get("last_status") or ""),
        "pending_first": bool(data.get("pending_first")),
    }


def active(now: int | None = None) -> bool:
    now = now or _now()
    return int(_load().get("until") or 0) > now


def remaining_s() -> int:
    until = int(_load().get("until") or 0)
    return max(0, until - _now())


def enable(*, minutes: int = 30, max_per_hour: int = MAX_PER_HOUR) -> dict[str, Any]:
    minutes = max(1, min(int(minutes), 180))
    max_per_hour = max(1, min(int(max_per_hour), 30))
    data = _load()
    data["until"] = _now() + minutes * 60
    data["max_per_hour"] = max_per_hour
    data["pending_first"] = True
    data["last_status"] = "armed"
    out = _save(data)
    try:
        from . import state as state_mod

        st = state_mod.load_state()
        st["auto_execute"] = True
        state_mod.save_state(st)
    except Exception:
        pass
    return snapshot()


def disable() -> dict[str, Any]:
    data = _load()
    data["until"] = 0
    data["pending_first"] = False
    data["last_status"] = "off"
    _save(data)
    try:
        from . import desk_wrap, state as state_mod

        desk_wrap.set_local()
        st = state_mod.load_state()
        st["auto_execute"] = False
        state_mod.save_state(st)
    except Exception:
        pass
    return snapshot()


def can_launch() -> tuple[bool, str]:
    if not active():
        return False, "window closed"
    data = _load()
    now = _now()
    launches = _prune(data.get("launches") or [], now=now)
    max_h = int(data.get("max_per_hour") or MAX_PER_HOUR)
    if len(launches) >= max_h:
        return False, f"cap {max_h}/hour"
    pid = int(data.get("pid") or 0)
    if pid and _pid_alive(pid):
        return False, "job already running"
    return True, "ok"


def can_recycle() -> tuple[bool, str]:
    if not active():
        return False, "window closed"
    data = _load()
    recycles = _prune(data.get("recycles") or [])
    if len(recycles) >= MAX_RECYCLE_PER_HOUR:
        return False, f"recycle cap {MAX_RECYCLE_PER_HOUR}/hour"
    return True, "ok"


def note_recycle() -> None:
    data = _load()
    times = _prune(data.get("recycles") or [])
    times.append(_now())
    data["recycles"] = times
    _save(data)


def prompt_block() -> str:
    snap = snapshot()
    if not snap["active"]:
        return ""
    mins = max(1, int(snap["remaining_s"]) // 60)
    return (
        f"SELF-REPAIR WINDOW is ON for about {mins} more minutes. "
        "You may tell humans to /fix a bug or /recycle the core terminal (:8787 only). "
        f"The implementer writes code (cap {snap['max_per_hour']}/hour). You still do not write production code. "
        "You may /read apps|scripts|tests|docs paths. No secrets. Do not reboot the whole PC."
    )


def _pid_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def _cursor_bin() -> str:
    found = shutil.which("cursor")
    if found:
        return found
    home = Path.home() / ".local" / "bin" / "cursor"
    if home.is_file():
        return str(home)
    return ""


def start_job(prompt: str, *, source: str = "manual") -> dict[str, Any]:
    ok, reason = can_launch()
    if not ok:
        return {"ok": False, "detail": reason}
    text = (prompt or "").strip() or FIRST_PROMPT
    cfg = load_config()
    data = _load()
    log = LOG_PATH
    log.parent.mkdir(parents=True, exist_ok=True)
    cursor = _cursor_bin()
    if cursor:
        cmd = [
            cursor,
            "agent",
            "-p",
            "--force",
            "--sandbox",
            "disabled",
            "--output-format",
            "text",
            text,
        ]
        env = os.environ.copy()
        if cfg.cursor_api_key:
            env["CURSOR_API_KEY"] = cfg.cursor_api_key
        handle = log.open("ab")
        try:
            proc = subprocess.Popen(
                cmd,
                cwd=str(REPO),
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=handle,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        finally:
            handle.close()
        launches = _prune(data.get("launches") or [])
        launches.append(_now())
        data["launches"] = launches
        data["pid"] = proc.pid
        data["pending_first"] = False
        data["last_status"] = f"local {source} pid={proc.pid}"
        _save(data)
        return {"ok": True, "mode": "local", "pid": proc.pid, "source": source}

    from . import cursor_api

    result = cursor_api.launch_agent(cfg, text)
    launches = _prune(data.get("launches") or [])
    launches.append(_now())
    data["launches"] = launches
    data["pending_first"] = False
    data["pid"] = 0
    data["last_status"] = "cloud " + source + (" ok" if result.get("ok") else " fail")
    _save(data)
    return {"ok": bool(result.get("ok")), "mode": "cloud", "source": source, "cursor": result}


def pump() -> dict[str, Any]:
    data = _load()
    pid = int(data.get("pid") or 0)
    if pid and not _pid_alive(pid):
        data["pid"] = 0
        data["last_status"] = "job finished"
        _save(data)
    if data.get("pending_first") and active():
        return start_job(FIRST_PROMPT, source="first")
    return snapshot()


def recycle_origin(*, force: bool = False) -> dict[str, Any]:
    data = _load()
    recycles = _prune(data.get("recycles") or [])
    if len(recycles) >= MAX_RECYCLE_PER_HOUR:
        return {"ok": False, "detail": f"recycle cap {MAX_RECYCLE_PER_HOUR}/hour"}
    if not force and not active():
        return {"ok": False, "detail": "window closed"}
    script = REPO / "scripts" / "recycle-origin.sh"
    if not script.is_file():
        return {"ok": False, "detail": "recycle script missing"}
    try:
        proc = subprocess.run(
            ["bash", str(script)],
            cwd=str(REPO),
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as e:
        return {"ok": False, "detail": type(e).__name__}
    note_recycle()
    blob = (proc.stdout or proc.stderr or "").strip()[:800]
    return {"ok": proc.returncode == 0, "detail": blob or f"exit {proc.returncode}"}
