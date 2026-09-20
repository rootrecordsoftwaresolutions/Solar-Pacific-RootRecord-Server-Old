"""Council Telegram posts from origin crons. No secrets."""
from __future__ import annotations

from typing import Any

from . import sanitize, state, telegram
from .config import load_config


def group_chat_id() -> str:
    cfg = load_config()
    st = state.load_state(cfg.state_path)
    return str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "").strip()


def post(voice: str, text: str) -> dict[str, Any]:
    voice = (voice or "ava").lower()
    if voice not in ("ava", "bruce", "carly"):
        voice = "ava"
    cfg = load_config()
    chat_id = group_chat_id()
    if not chat_id:
        return {"ok": False, "detail": "no group chat"}
    token = cfg.token_for(voice)
    if not token:
        return {"ok": False, "detail": f"no token for {voice}"}
    body = sanitize.sanitize_outbound((text or "").strip(), voice=voice)
    if not body:
        return {"ok": False, "detail": "empty"}
    raw = telegram.send_message(token, chat_id, body)
    if raw.get("ok"):
        try:
            from apps.core.services import ollama_lifecycle

            ollama_lifecycle.on_ai_use()
        except Exception:
            pass
    return {"ok": bool(raw.get("ok")), "voice": voice, "raw": raw}
