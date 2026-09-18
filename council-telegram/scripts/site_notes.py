"""Capture site notes when a human says note/notes — last ~10 human lines."""
from __future__ import annotations

import sys
import time
from pathlib import Path
from typing import Any

_NOTES_SCRIPTS = Path.home() / ".ollama" / "skills" / "notes" / "scripts"
_COOLDOWN: dict[str, float] = {}
_COOLDOWN_S = 45.0


def _notes_mod():
    if str(_NOTES_SCRIPTS) not in sys.path:
        sys.path.insert(0, str(_NOTES_SCRIPTS))
    import notes as notes_mod  # type: ignore

    return notes_mod


def wants_note(text: str) -> bool:
    try:
        return bool(_notes_mod().wants_note(text))
    except Exception:
        return False


def capture(
    *,
    cfg: Any,
    chat_id: int | str,
    text: str,
    display: str,
    username: str | None,
    message_id: Any,
    reply_voice: str = "ava",
    extra_rows: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Read recent human chat, save one generalized note, ack in Telegram."""
    from . import chatlog, telegram

    key = str(chat_id)
    now = time.time()
    if now - float(_COOLDOWN.get(key) or 0) < _COOLDOWN_S:
        return {"ok": False, "skipped": "cooldown"}

    notes = _notes_mod()
    rows = list(chatlog.recent_humans(chat_id, 10))
    # Ensure the triggering line is present (may not be flushed yet).
    trig = {
        "dir": "in",
        "chat_id": str(chat_id),
        "display": display or username or "someone",
        "username": username,
        "text": text,
        "message_id": message_id,
    }
    if not rows or str(rows[-1].get("text") or "") != str(text or "").strip():
        rows = rows + [trig]
    if extra_rows:
        # Prepend recovered context (oldest first), then recent.
        rows = list(extra_rows) + rows
    # Dedupe by text
    seen: set[str] = set()
    deduped: list[dict[str, Any]] = []
    for r in rows:
        t = " ".join(str(r.get("text") or "").split()).strip().lower()
        if not t or t in seen:
            continue
        seen.add(t)
        deduped.append(r)
    rows = deduped[-12:]

    got = notes.capture_from_humans(rows, trigger=text, source="council-chat")
    if not got.get("ok"):
        return got

    _COOLDOWN[key] = now
    nid = got.get("id")
    body = str(got.get("body") or "")
    preview = body if len(body) <= 280 else body[:277] + "…"
    ack = f"Noted — saved `{nid}`.\n{preview}"
    try:
        telegram.send_message(
            cfg.token_for(reply_voice if reply_voice in ("ava", "bruce", "carly") else "ava"),
            chat_id,
            ack,
            reply_to=message_id,
        )
    except Exception as e:  # noqa: BLE001
        got["ack_error"] = f"{type(e).__name__}: {e}"
    print(f"site-note id={nid} chars={len(body)}", flush=True)
    got["acked"] = True
    return got
