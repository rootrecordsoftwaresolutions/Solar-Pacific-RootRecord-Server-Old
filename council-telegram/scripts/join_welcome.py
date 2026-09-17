"""Three-voice welcome when a human is added to the council group. No LLM wait."""
from __future__ import annotations

from typing import Any

from . import trust


def _who(name: str, username: str | None) -> str:
    first = (name or "").strip() or "there"
    uname = (username or "").lstrip("@").strip()
    if uname:
        return f"{first} (@{uname})"
    return first


def greet_lines(
    *,
    name: str,
    score: int,
    username: str | None = None,
) -> tuple[tuple[str, str], ...]:
    who = _who(name, username)
    first = (name or "").strip() or "there"
    try:
        score = int(score)
    except (TypeError, ValueError):
        score = trust.DEFAULT_SCORE
    if score >= trust.OWNER_SCORE:
        ava = f"Welcome, {first}. You're home. Full desk."
        bruce = (
            f"{who} is Owner. Commands still go through Ava. "
            "The rest of us follow your call."
        )
        carly = f"Welcome, {first}. Owner desk. Still don't paste secrets."
    elif score >= trust.REQUEST_EXECUTE_MIN:
        ava = f"Welcome, {first}. Glad you're here. You can talk."
        bruce = "You can talk. Don't paste tokens."
        carly = f"Welcome, {first}. No tokens in chat."
    elif score >= trust.CONTRIBUTE_MIN:
        ava = (
            f"Welcome, {first}. Glad you're here. We hear you, and you can join the talk."
        )
        bruce = "You can contribute. Don't paste tokens or secrets."
        carly = (
            f"Welcome, {first}. Don't paste tokens or secrets. Kindness is fine."
        )
    else:
        ava = (
            f"Welcome, {first}. You're in. We stay quiet unless someone @'s you."
        )
        bruce = "Contribute opens once you're established. Don't dump links."
        carly = (
            f"Welcome, {first}. We stay quiet unless someone @'s you. "
            "No links dump, no secrets."
        )
    return (("ava", ava), ("bruce", bruce), ("carly", carly))


def announce(
    cfg: Any,
    chat_id: int | str,
    *,
    name: str,
    score: int,
    username: str | None = None,
    poster=None,
) -> dict[str, Any]:
    """Post Ava, Bruce, and Carly welcome lines. poster(voice, text) for tests."""
    post = poster
    if post is None:
        from . import sanitize, telegram as tg

        def post(voice: str, text: str) -> dict[str, Any]:
            token = cfg.token_for(voice)
            if not token:
                return {"ok": False, "detail": f"no token for {voice}"}
            body = sanitize.sanitize_outbound((text or "").strip(), voice=voice, allow_operator_name=True)
            if not body:
                return {"ok": False, "detail": "empty"}
            raw = tg.send_message(token, chat_id, body)
            if raw.get("ok"):
                try:
                    from apps.core.services import ollama_lifecycle

                    ollama_lifecycle.on_ai_use()
                except Exception:
                    pass
            return {"ok": bool(raw.get("ok")), "voice": voice, "raw": raw}

    sent = 0
    lines = greet_lines(name=name, score=score, username=username)
    for voice, text in lines:
        try:
            res = post(voice, text)
            if isinstance(res, dict) and res.get("ok") is False:
                continue
            sent += 1
        except Exception as exc:  # noqa: BLE001
            print(f"join-welcome {voice} fail: {type(exc).__name__}", flush=True)
    print(f"join-welcome sent={sent} score={score} name={name!r}", flush=True)
    return {"ok": True, "sent": sent, "score": score, "lines": lines}
