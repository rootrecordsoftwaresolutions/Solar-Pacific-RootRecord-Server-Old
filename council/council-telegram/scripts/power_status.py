"""Immediate Telegram status lines on power up/down. No Ollama wait."""
from __future__ import annotations

import json
import time
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "power-status.json"
DEBOUNCE_S = 20
# Auto idle/wake posts. Owner /resume and Console start/stop use force=True.
MIN_AUTO_S = 15 * 60

LINES: dict[str, tuple[tuple[str, str], ...]] = {
    "up": (
        ("ava", "Coming online."),
        ("bruce", "Desk is live. Brain spinning up."),
        ("carly", "Online. Keep secrets out of chat."),
    ),
    "down": (
        ("ava", "Powering down. Owner commands still land."),
        ("bruce", "Ollama off. Files stay on disk."),
        ("carly", "Going dark."),
    ),
}


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def should_announce(kind: str, *, now: int | None = None, force: bool = False) -> bool:
    kind = "up" if kind == "up" else "down"
    now = int(now if now is not None else time.time())
    row = _load()
    last_k = str(row.get("kind") or "")
    last_at = int(row.get("at") or 0)
    if not last_at:
        return True
    gap = now - last_at
    if last_k == kind and gap < DEBOUNCE_S:
        return False
    if force:
        return True
    if gap < MIN_AUTO_S:
        return False
    return True


def announce(kind: str, *, poster=None, force: bool = False) -> dict[str, Any]:
    """Post canned Ava/Bruce/Carly lines immediately. poster(voice, text) for tests."""
    kind = "up" if (kind or "").lower() in {"up", "start", "wake", "loading", "online"} else "down"
    now = int(time.time())
    if not should_announce(kind, now=now, force=force):
        return {"ok": True, "skipped": True, "kind": kind}
    post = poster
    if post is None:
        # No Telegram ceremony on console/origin recycle. Tests pass poster=.
        _save({"kind": kind, "at": now, "sent": 0})
        print(f"power-status {kind} silent", flush=True)
        return {"ok": True, "skipped": True, "kind": kind, "silent": True}
    sent = 0
    for voice, text in LINES[kind]:
        try:
            res = post(voice, text)
            if isinstance(res, dict) and res.get("ok") is False:
                continue
            sent += 1
        except Exception as exc:  # noqa: BLE001
            print(f"power-status {kind} {voice} fail: {type(exc).__name__}", flush=True)
    _save({"kind": kind, "at": now, "sent": sent})
    print(f"power-status {kind} sent={sent}", flush=True)
    return {"ok": True, "skipped": False, "kind": kind, "sent": sent}
