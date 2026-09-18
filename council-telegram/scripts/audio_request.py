"""Team-chat /audio: Ava asks who speaks, then for the script, then posts a WAV."""
from __future__ import annotations

import json
import re
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from . import telegram
from .config import CONFIG_DIR, Config

HST = ZoneInfo("Pacific/Honolulu")
PATH = CONFIG_DIR / "audio-request.json"
ASK_IDLE_S = 20 * 60
MAX_CHARS = 12000
ASK_VOICE = "Who should speak? Reply Ava, Bruce, or Carly."
ASK_TEXT = "Please post the full content you wish to put in voice"
VOICES = {
    "ava": "Ava",
    "bruce": "Bruce",
    "carly": "Carly",
}


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"status": "idle"}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"status": "idle"}
    return data if isinstance(data, dict) else {"status": "idle"}


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    payload = dict(data)
    payload["updated"] = int(time.time())
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def status() -> str:
    return str(_load().get("status") or "idle")


def parse_voice(text: str) -> str | None:
    low = re.sub(r"[^a-z]+", " ", (text or "").lower()).strip()
    if not low:
        return None
    if re.search(r"\b(ava|ayeva|heart)\b", low):
        return "ava"
    if re.search(r"\b(bruce|echo)\b", low):
        return "bruce"
    if re.search(r"\b(carly|nova)\b", low):
        return "carly"
    return None


def _expired(sess: dict[str, Any]) -> bool:
    asked = int(sess.get("asked_at") or 0)
    st = str(sess.get("status") or "idle")
    if st == "idle" or not asked:
        return False
    return int(time.time()) - asked > ASK_IDLE_S


def _say(
    cfg: Config,
    chat_id: int | str,
    msg: str,
    *,
    reply_to: int | None = None,
    thread_id: int | None = None,
) -> bool:
    # Prompts are plain group messages (no reply_to) so they stay visible in the main feed.
    out = telegram.send_message(
        cfg.token_for("ava"),
        chat_id,
        msg,
        reply_to=None,
        message_thread_id=thread_id,
    )
    if out.get("ok"):
        print(f"audio-request sent chat={chat_id} text={msg[:60]!r}", flush=True)
        return True
    print(
        f"audio-request send fail {out.get('error_code')} {(out.get('description') or '')[:180]}",
        flush=True,
    )
    # One bare retry if the first attempt used a thread id.
    if thread_id is not None:
        out = telegram.send_message(
            cfg.token_for("ava"),
            chat_id,
            msg,
            reply_to=None,
            message_thread_id=None,
        )
        if out.get("ok"):
            print(f"audio-request sent-retry chat={chat_id} text={msg[:60]!r}", flush=True)
            return True
        print(
            f"audio-request retry fail {out.get('error_code')} {(out.get('description') or '')[:180]}",
            flush=True,
        )
    return False


def _cancel() -> None:
    _save({"status": "idle"})


def _start(
    cfg: Config,
    chat_id: int | str,
    uid: int | str | None,
    raw: str,
    *,
    reply_to: int | None = None,
    thread_id: int | None = None,
) -> bool:
    rest = re.sub(r"^/audio(?:@[A-Za-z0-9_]+)?\s*", "", raw or "", flags=re.I).strip()
    voice = parse_voice(rest)
    body = rest
    if voice:
        body = re.sub(r"(?i)\b(ava ivy|ava|ayeva|heart|bruce monitor|bruce|echo|carly mal|carly|nova)\b", "", rest).strip()
        body = re.sub(r"\s+", " ", body).strip(" ,.-")
    sess = {
        "status": "ask_text" if voice else "ask_voice",
        "chat_id": str(chat_id),
        "uid": uid,
        "voice": voice or "",
        "asked_at": int(time.time()),
        "thread_id": thread_id,
    }
    _save(sess)
    if voice and len(body) >= 8:
        return _finish(cfg, chat_id, voice, body, reply_to=reply_to, thread_id=thread_id)
    if voice:
        _say(cfg, chat_id, ASK_TEXT, reply_to=reply_to, thread_id=thread_id)
        return True
    _say(cfg, chat_id, ASK_VOICE, reply_to=reply_to, thread_id=thread_id)
    return True


def _finish(
    cfg: Config,
    chat_id: int | str,
    voice: str,
    body: str,
    *,
    reply_to: int | None = None,
    thread_id: int | None = None,
) -> bool:
    script = " ".join((body or "").split()).strip()[:MAX_CHARS]
    if len(script) < 8:
        _say(
            cfg,
            chat_id,
            ASK_TEXT,
            reply_to=reply_to,
            thread_id=thread_id,
        )
        return True
    _say(cfg, chat_id, f"Making the WAV with {VOICES.get(voice, voice)}…", reply_to=reply_to, thread_id=thread_id)
    try:
        from apps.core import config as origin_config
        from apps.voice.speakers import speak_report

        stamp = datetime.now(HST).strftime("%Y%m%d-%H%M")
        dest = origin_config.GENERATED_DIR / f"team-audio-{voice}-{stamp}.wav"
        built = speak_report(voice, script, dest)
    except Exception as exc:
        _save({"status": "idle"})
        _say(cfg, chat_id, f"Could not make the WAV. {str(exc)[:180]}", reply_to=reply_to, thread_id=thread_id)
        return True
    _save({"status": "idle"})
    wav = Path(str(built.get("wav") or dest))
    if not built.get("ok") or not wav.is_file() or wav.stat().st_size <= 0:
        _say(
            cfg,
            chat_id,
            built.get("detail") or "No WAV. Paste the words again.",
            reply_to=reply_to,
            thread_id=thread_id,
        )
        return True
    sent = telegram.send_audio(
        cfg.token_for("ava"),
        chat_id,
        wav,
        caption=f"{VOICES.get(voice, voice)} · {wav.name}",
    )
    if not sent.get("ok"):
        _say(
            cfg,
            chat_id,
            f"WAV is ready on disk as {wav.name}, but Telegram did not take the voice note ({sent.get('description') or 'fail'}).",
            reply_to=reply_to,
            thread_id=thread_id,
        )
    return True


def handle_text(
    cfg: Config,
    chat_id: int | str,
    uid: int | str | None,
    text: str,
    *,
    reply_to: int | None = None,
    thread_id: int | None = None,
) -> bool:
    """True when /audio or a pending audio answer consumed this message."""
    raw = (text or "").strip()
    if not raw:
        return False
    low = raw.lower()
    sess = _load()
    st = str(sess.get("status") or "idle")
    if _expired(sess):
        _cancel()
        sess = {"status": "idle"}
        st = "idle"

    if low.startswith("/audio"):
        rest = re.sub(r"^/audio(?:@[A-Za-z0-9_]+)?\s*", "", low, flags=re.I).strip()
        if rest in {"cancel", "stop", "nevermind", "never mind"}:
            _cancel()
            _say(cfg, chat_id, "Audio request cancelled.", reply_to=reply_to, thread_id=thread_id)
            return True
        return _start(cfg, chat_id, uid, raw, reply_to=reply_to, thread_id=thread_id)

    if st == "idle":
        return False
    if str(sess.get("chat_id") or "") != str(chat_id):
        return False
    # Team chat: anyone in this group may answer the voice/script prompts.
    # /audio itself stays owner-gated in __main__.
    if low in {"/cancel", "cancel", "stop"}:
        _cancel()
        _say(cfg, chat_id, "Audio request cancelled.", reply_to=reply_to, thread_id=thread_id)
        return True
    if raw.startswith("/") and not low.startswith("/audio"):
        return False

    if st == "ask_voice":
        voice = parse_voice(raw)
        if not voice:
            _say(cfg, chat_id, ASK_VOICE, reply_to=reply_to, thread_id=thread_id)
            return True
        sess["status"] = "ask_text"
        sess["voice"] = voice
        sess["asked_at"] = int(time.time())
        _save(sess)
        _say(cfg, chat_id, ASK_TEXT, reply_to=reply_to, thread_id=thread_id)
        return True

    if st == "ask_text":
        voice = str(sess.get("voice") or "")
        if voice not in VOICES:
            sess["status"] = "ask_voice"
            sess["asked_at"] = int(time.time())
            _save(sess)
            _say(cfg, chat_id, ASK_VOICE, reply_to=reply_to, thread_id=thread_id)
            return True
        return _finish(cfg, chat_id, voice, raw, reply_to=reply_to, thread_id=thread_id)
    return False
