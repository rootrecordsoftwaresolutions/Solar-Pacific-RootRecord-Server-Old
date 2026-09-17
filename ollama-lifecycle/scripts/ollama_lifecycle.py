"""Wake local AI on inbound chat. After 15 minutes unused, stop Ollama serve.

FastFlowLM stays mapped while AVA Console is up. Idle-stop unmaps it on close.
Brainstorm / queue busy keeps Ollama up. Does not run idle-stop.sh.
Loading / power-down clips play on the down↔up edges only.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any

from apps.core import config

IDLE_S = 15 * 60
STATE_PATH = config.STATE_DIR / "ollama-lifecycle.json"
WORDS_DIR = config.ASSETS_DIR / "words"
LOADING_NAME = "agents_are_loading.wav"
DOWN_NAME = "agents_are_powering_down.wav"
SOURCE_LOADING = Path("/home/rootrecord/Downloads/agents_are_loading.wav")
SOURCE_DOWN = Path("/home/rootrecord/Downloads/agents_are_powering_down.wav")


def _load() -> dict[str, Any]:
    if not STATE_PATH.is_file():
        return {}
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(STATE_PATH)


def ensure_clips() -> None:
    WORDS_DIR.mkdir(parents=True, exist_ok=True)
    for src, name in ((SOURCE_LOADING, LOADING_NAME), (SOURCE_DOWN, DOWN_NAME)):
        dest = WORDS_DIR / name
        if dest.is_file() and dest.stat().st_size > 0:
            continue
        if src.is_file() and src.stat().st_size > 0:
            shutil.copy2(src, dest)


def clip_path(kind: str) -> Path | None:
    ensure_clips()
    name = LOADING_NAME if kind == "loading" else DOWN_NAME
    path = WORDS_DIR / name
    return path if path.is_file() else None


def play_clip(kind: str) -> bool:
    path = clip_path(kind)
    if not path:
        return False
    ffplay = shutil.which("ffplay")
    if not ffplay:
        return False
    try:
        subprocess.Popen(
            [ffplay, "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        return True
    except OSError:
        return False


def _cfg():
    from apps.council.config import load_config

    return load_config()


def _up() -> bool:
    from apps.council import ollama_ctl

    return ollama_ctl.is_up(_cfg())


def _busy() -> bool:
    from apps.council import queue

    if queue.pending_count() or queue.peek():
        return True
    data = queue.load()
    if any(j.get("status") == "running" for j in data.get("jobs") or []):
        return True
    try:
        from apps.council import brainstorm

        if brainstorm.status() in {"running", "wrapping"}:
            return True
    except Exception:
        pass
    return False


def touch(*, source: str = "") -> None:
    row = _load()
    row["last_at"] = int(time.time())
    if source:
        row["last_source"] = source
    _save(row)


def on_message(*, source: str) -> dict[str, Any]:
    """Any human inbound on Telegram / Discord / Slack."""
    from apps.council import ollama_ctl

    cfg = _cfg()
    was_up = ollama_ctl.is_up(cfg)
    flm_up = False
    try:
        import os

        want_npu = os.getenv("AVA_NPU_CHAT", "1").strip().lower() not in {"0", "false", "off", "no"}
        if want_npu:
            if not ollama_ctl.flm_is_up() and not os.getenv("PYTEST_CURRENT_TEST"):
                ollama_ctl.flm_start()
            flm_up = ollama_ctl.flm_is_up()
    except Exception:
        flm_up = False
    touch(source=source)
    if flm_up:
        row = _load()
        row["powered"] = "up"
        row["engine"] = "npu"
        _save(row)
        if was_up:
            return {"ok": True, "started": False, "played": False, "engine": "npu"}
        play_clip("loading")
        try:
            from apps.council.power_status import announce

            announce("up")
        except Exception:
            pass
        return {"ok": True, "started": True, "played": True, "engine": "npu"}
    try:
        import os

        want_npu = os.getenv("AVA_NPU_CHAT", "1").strip().lower() not in {"0", "false", "off", "no"}
    except Exception:
        want_npu = True
    if want_npu:
        return {"ok": False, "started": False, "played": False, "engine": "npu-down"}
    if was_up:
        return {"ok": True, "started": False, "played": False}
    play_clip("loading")
    try:
        from apps.council.power_status import announce

        announce("up")
    except Exception:
        pass
    started = ollama_ctl.start(cfg)
    if started:
        try:
            ollama_ctl.warm_default(cfg)
        except Exception:
            pass
    row = _load()
    row["powered"] = "up" if started else "down"
    _save(row)
    return {"ok": started, "started": started, "played": True}


def _chat_activity_ts() -> int:
    """Latest human or council line in the group log."""
    try:
        from apps.council import chatlog

        best = 0
        for row in chatlog.recent(n=40):
            ts = int(row.get("ts") or 0)
            if ts > best:
                best = ts
        return best
    except Exception:
        return 0


def last_activity_ts() -> int:
    last = int(_load().get("last_at") or 0)
    chat = _chat_activity_ts()
    return max(last, chat)


def on_ai_use() -> None:
    touch(source="ai")


def tick_idle() -> dict[str, Any]:
    from apps.council import ollama_ctl

    cfg = _cfg()
    if _busy():
        touch(source="busy")
        return {"ok": True, "stopped": False, "reason": "busy"}
    row = _load()
    last = last_activity_ts()
    now = int(time.time())
    if not last:
        touch(source="seed")
        return {"ok": True, "stopped": False, "reason": "seeded"}
    if now - last < IDLE_S:
        return {"ok": True, "stopped": False, "reason": "active"}
    ollama_up = ollama_ctl.is_up(cfg)
    flm_up = ollama_ctl.flm_is_up()
    # While AVA Console is up, keep FastFlowLM mapped. Unmapping it dumps chat onto
    # GGUF / 840M / RAM and this 16 GB box crashes. Idle-stop unmaps at console close.
    if flm_up:
        stopped_ollama = ollama_ctl.stop_serve(cfg) if ollama_up else False
        if stopped_ollama:
            row = _load()
            row["last_stop"] = now
            row["engine"] = "npu"
            _save(row)
        return {"ok": True, "stopped": False, "reason": "npu_session"}
    if not ollama_up:
        return {"ok": True, "stopped": False, "reason": "already_down"}
    try:
        from apps.council.power_status import announce

        announce("down")
    except Exception:
        pass
    play_clip("down")
    stopped_ollama = ollama_ctl.stop_serve(cfg)
    if stopped_ollama:
        row = _load()
        row["powered"] = "down"
        row["last_stop"] = now
        _save(row)
    return {"ok": True, "stopped": bool(stopped_ollama), "reason": "idle"}
