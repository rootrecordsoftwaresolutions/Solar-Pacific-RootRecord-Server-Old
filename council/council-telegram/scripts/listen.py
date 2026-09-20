"""Per-bot Telegram listen. Group chat stays on Ava. DMs go to that bot only."""
from __future__ import annotations

import threading
import time
import traceback
from typing import Any, Callable

from . import state, telegram
from .config import Config

HANDLE_LOCK = threading.Lock()
OFFSET_KEY = {
    "ava": "update_offset_ava",
    "bruce": "update_offset_bruce",
    "carly": "update_offset_carly",
}


def is_private_chat(chat: dict[str, Any] | None) -> bool:
    return str((chat or {}).get("type") or "") == "private"


def chat_from_update(update: dict[str, Any]) -> dict[str, Any]:
    cm = update.get("chat_member") or update.get("my_chat_member") or {}
    if isinstance(cm, dict) and isinstance(cm.get("chat"), dict):
        return cm["chat"]
    msg = update.get("message") or update.get("edited_message") or {}
    if isinstance(msg, dict) and isinstance(msg.get("chat"), dict):
        return msg["chat"]
    return {}


def should_handle(update: dict[str, Any], *, listen_voice: str, private_only: bool) -> bool:
    if not private_only:
        return True
    return is_private_chat(chat_from_update(update))


def dm_route(listen_voice: str) -> dict[str, Any]:
    voice = listen_voice if listen_voice in ("ava", "bruce", "carly") else "ava"
    return {
        "voices": [voice],
        "reason": "dm",
        "group": False,
        "round": False,
        "pass_on": False,
    }


def start_pollers(
    cfg: Config,
    *,
    handle: Callable[..., None],
) -> None:
    """Bruce/Carly long-poll private chats only. Ava stays on the main loop."""
    for voice in ("bruce", "carly"):
        token = cfg.token_for(voice)
        if not token:
            print(f"dm-poll skip {voice}: no token", flush=True)
            continue
        telegram.delete_webhook(token)
        t = threading.Thread(
            target=_poll_loop,
            args=(cfg, voice, token, handle),
            name=f"tg-{voice}-dm",
            daemon=True,
        )
        t.start()
        print(f"dm-poll started {voice}", flush=True)


def _poll_loop(
    cfg: Config,
    voice: str,
    token: str,
    handle: Callable[..., None],
) -> None:
    from . import burst as _burst
    from . import queue as _queue
    from . import trust
    from . import worker

    key = OFFSET_KEY[voice]
    while True:
        try:
            st = state.load_state(cfg.state_path)
            offset = int(st.get(key) or 0)
            try:
                with HANDLE_LOCK:
                    _burst.tick(cfg, handle)
            except Exception:
                traceback.print_exc()
            st = state.load_state(cfg.state_path)
            drain = bool(not st.get("busy") and _queue.peek())
            resp = telegram.get_updates(
                token, offset=offset, timeout=_burst.poll_timeout(25, drain=drain)
            )
            if not resp.get("ok"):
                desc = str(resp.get("description") or "")[:180]
                print(f"getUpdates {voice} fail {resp.get('error_code')} {desc}", flush=True)
                time.sleep(2)
                continue
            for upd in resp.get("result") or []:
                uid_upd = int(upd.get("update_id", 0))
                if not should_handle(upd, listen_voice=voice, private_only=True):
                    offset = max(offset, uid_upd + 1)
                    continue
                msg = upd.get("message") or upd.get("edited_message") or {}
                frm = msg.get("from") or {}
                preview = str(msg.get("text") or msg.get("caption") or "")[:80].replace("\n", " ")
                if msg:
                    print(
                        f"inbound voice={voice} chat_type=private from={frm.get('username') or frm.get('id')} "
                        f"chat={((msg.get('chat') or {}).get('id'))} text={preview!r}",
                        flush=True,
                    )
                with HANDLE_LOCK:
                    st = state.load_state(cfg.state_path)
                    trust_data = trust.load_trust(cfg.trust_path)
                    try:
                        handle(cfg, st, trust_data, upd, listen_voice=voice)
                    except Exception:
                        traceback.print_exc()
                    st = state.load_state(cfg.state_path)
                    offset = max(offset, uid_upd + 1)
                    st[key] = offset
                    state.save_state(st)
            if resp.get("result"):
                st = state.load_state(cfg.state_path)
                st[key] = offset
                state.save_state(st)
            try:
                with HANDLE_LOCK:
                    _burst.tick(cfg, handle)
            except Exception:
                traceback.print_exc()
            st = state.load_state(cfg.state_path)
            if not st.get("busy"):
                try:
                    worker.process_one(cfg, st)
                except Exception:
                    traceback.print_exc()
        except Exception:
            traceback.print_exc()
            time.sleep(3)
