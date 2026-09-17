"""Persisted council state (mode, discussion, owner, busy, offset)."""
from __future__ import annotations

import json
import threading
import time
from pathlib import Path
from typing import Any

from .config import STATE_PATH, Config

_lock = threading.RLock()

DEFAULT_STATE: dict[str, Any] = {
    "mode": "auto",  # auto | casual | council
    "discussion": "on",  # on | off
    "auto_execute": False,
    "busy": False,
    "busy_started": 0,
    "busy_note": "",
    "owner_id": "",
    "group_chat_id": "",
    "update_offset": 0,
    "updated": 0,
}


def _now() -> int:
    return int(time.time())


def load_state(path: Path | None = None) -> dict[str, Any]:
    p = path or STATE_PATH
    with _lock:
        if not p.is_file():
            st = dict(DEFAULT_STATE)
            st["updated"] = _now()
            return st
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
        st = dict(DEFAULT_STATE)
        st.update({k: v for k, v in data.items() if k in DEFAULT_STATE or True})
        return st


def save_state(state: dict[str, Any], path: Path | None = None) -> None:
    p = path or STATE_PATH
    with _lock:
        state = dict(state)
        state["updated"] = _now()
        p.parent.mkdir(parents=True, exist_ok=True)
        tmp = p.with_suffix(".tmp")
        tmp.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp.replace(p)


def bind_owner(state: dict[str, Any], user_id: int | str, cfg: Config | None = None) -> dict[str, Any]:
    uid = str(user_id).strip()
    if not uid:
        return state
    if state.get("owner_id"):
        return state
    state["owner_id"] = uid
    save_state(state)
    if cfg is not None and not cfg.alexander_telegram_id:
        from .config import upsert_secret

        upsert_secret("ALEXANDER_TELEGRAM_ID", uid)
        cfg.alexander_telegram_id = uid
    return state


def owner_bound(state: dict[str, Any], cfg: Config) -> bool:
    return bool(str(state.get("owner_id") or cfg.alexander_telegram_id or "").strip())


def owner_id(state: dict[str, Any], cfg: Config) -> str:
    return str(state.get("owner_id") or cfg.alexander_telegram_id or "").strip()


def is_owner(state: dict[str, Any], cfg: Config, user_id: int | str) -> bool:
    oid = owner_id(state, cfg)
    if not oid:
        return False
    return str(user_id).strip() == oid


def set_group_chat_id(state: dict[str, Any], chat_id: int | str, cfg: Config) -> dict[str, Any]:
    cid = str(chat_id).strip()
    if not cid:
        return state
    if not state.get("group_chat_id"):
        state["group_chat_id"] = cid
        save_state(state)
    if not cfg.telegram_group_chat_id:
        from .config import upsert_secret

        upsert_secret("TELEGRAM_GROUP_CHAT_ID", cid)
        cfg.telegram_group_chat_id = cid
    return state


def status_lines(state: dict[str, Any], cfg: Config, ollama_up: bool) -> str:
    bound = owner_bound(state, cfg)
    return (
        f"discussion={state.get('discussion', 'on')}\n"
        f"mode={state.get('mode', 'auto')}\n"
        f"ollama-up={str(ollama_up).lower()}\n"
        f"owner-bound={str(bound).lower()}\n"
        f"auto_execute={str(bool(state.get('auto_execute'))).lower()}\n"
        f"busy={str(bool(state.get('busy'))).lower()}\n"
        f"group-chat-set={str(bool(state.get('group_chat_id') or cfg.telegram_group_chat_id)).lower()}"
    )
