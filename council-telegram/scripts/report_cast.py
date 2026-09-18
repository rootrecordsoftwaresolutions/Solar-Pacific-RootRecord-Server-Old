"""Bruce posts system / NWS / audio reports to the council group with transcripts.

Replies on those posts are stored as improvement notes. No Ollama.
"""
from __future__ import annotations

import hashlib
import json
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR
from . import sanitize, state, telegram
from .config import Config, load_config

PATH = CONFIG_DIR / "report-cast.json"
NOTES = CONFIG_DIR / "report-notes.jsonl"
MAX_POSTS = 200
NOTE_HINT = "Reply to this with notes if the report should be better."

TITLES = {
    "system": "System performance",
    "nws": "NWS Hawaiʻi",
    "weather": "NWS Hawaiʻi",
    "solar": "Hourly solar",
    "kilauea": "Kīlauea",
    "security": "Security",
    "bandwidth": "Bandwidth",
    "remaining": "Remaining tasks",
    "morning": "Morning status",
    "midday": "Midday status",
    "evening": "Evening status",
    "late": "Late status",
    "boot": "Boot status",
    "hurricane": "Hurricane desk",
    "earthquake": "Earthquake",
    "energy": "Energy desk",
}


def poster_voice(kind: str) -> str:
    try:
        from apps.voice.speakers import agent_for

        return agent_for(kind)
    except Exception:
        return "ava"


def readable_script(script: str) -> str:
    return " ".join((script or "").replace("_", " ").split())


def title_for(kind: str) -> str:
    key = (kind or "report").strip().lower()
    if key == "weather":
        key = "nws"
    return TITLES.get(key, f"{key} report")


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"posts": {}, "last": {}}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"posts": {}, "last": {}}
    if not isinstance(data, dict):
        return {"posts": {}, "last": {}}
    data.setdefault("posts", {})
    data.setdefault("last", {})
    return data


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def _digest(kind: str, transcript: str, audio: Path | None) -> str:
    """Skip on same spoken/written body. Restitching WAV is not a new report."""
    h = hashlib.sha256()
    h.update((kind or "").encode())
    h.update(b"\0")
    h.update((transcript or "").encode("utf-8", errors="replace"))
    return h.hexdigest()


def _remember(data: dict[str, Any], message_id: int | None, kind: str, digest: str) -> None:
    if message_id is None:
        return
    posts = data.setdefault("posts", {})
    posts[str(int(message_id))] = {
        "kind": kind,
        "digest": digest,
        "ts": int(time.time()),
    }
    if len(posts) > MAX_POSTS:
        keep = sorted(posts.items(), key=lambda kv: int((kv[1] or {}).get("ts") or 0))[-MAX_POSTS:]
        data["posts"] = {k: v for k, v in keep}


def lookup(reply_id: int | None) -> dict[str, Any] | None:
    if reply_id is None:
        return None
    row = (_load().get("posts") or {}).get(str(int(reply_id)))
    return row if isinstance(row, dict) else None


def _chat_id(cfg: Config) -> str:
    st = state.load_state(cfg.state_path)
    return str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "").strip()


def _touch() -> None:
    try:
        from apps.core.services import ollama_lifecycle

        ollama_lifecycle.on_ai_use()
    except Exception:
        pass


def _mid(raw: dict[str, Any]) -> int | None:
    if not raw.get("ok"):
        return None
    result = raw.get("result")
    if isinstance(result, dict) and result.get("message_id") is not None:
        return int(result["message_id"])
    return None


def transcript_body(kind: str, transcript: str) -> str:
    text = (transcript or "").strip()
    if not text:
        return ""
    head = title_for(kind)
    return f"{head} — transcript\n\n{text}"


def caption_for(kind: str) -> str:
    return f"{title_for(kind)}\n{NOTE_HINT}"


def notify_report(
    kind: str,
    *,
    transcript: str,
    audio: str | Path | None = None,
    photo: str | Path | None = None,
    title: str | None = None,
    cfg: Config | None = None,
    poster=None,
) -> dict[str, Any]:
    """Post as the locked speaker for that desk: audio plus transcript."""
    kind = (kind or "report").strip().lower()
    if kind == "weather":
        kind = "nws"
    # Boot files are catch-up only (boot_brief). Never spam from prelim rewrites.
    if kind == "boot":
        return {"ok": True, "skipped": True, "detail": "boot_catchup_only", "kind": kind}
    voice = poster_voice(kind)
    raw_t = (transcript or "").strip()
    if kind in {"solar", "kilauea", "remaining", "energy"} or ("\n" not in raw_t and "_" in raw_t):
        text = readable_script(raw_t) if kind != "energy" else raw_t
    else:
        text = raw_t
    audio_path = Path(audio) if audio else None
    if audio_path is not None and not audio_path.is_file():
        audio_path = None
    photo_path = Path(photo) if photo else None
    if photo_path is not None and not photo_path.is_file():
        photo_path = None
    if not text and audio_path is None and photo_path is None:
        return {"ok": False, "detail": "empty"}
    digest = _digest(kind, text, audio_path)
    if photo_path is not None:
        digest = hashlib.sha256(
            (digest + "\0" + str(photo_path) + "\0" + str(int(photo_path.stat().st_mtime))).encode()
        ).hexdigest()
    data = _load()
    last = data.setdefault("last", {})
    if str(last.get(kind) or "") == digest:
        return {"ok": True, "skipped": True, "detail": "unchanged", "kind": kind}
    cfg = cfg or load_config()
    chat_id = _chat_id(cfg)
    if not chat_id:
        return {"ok": False, "detail": "no group chat"}
    token = ""
    if poster is None:
        token = cfg.token_for(voice)
        if not token:
            return {"ok": False, "detail": f"no {voice} token"}

    cap_src = f"{title.strip()}\n{NOTE_HINT}" if (title or "").strip() else caption_for(kind)
    cap = sanitize.sanitize_outbound(cap_src, voice=voice)[:900]
    body = sanitize.sanitize_outbound(transcript_body(kind, text), voice=voice)
    ids: list[int] = []

    def send_audio_fn(path: Path, caption: str) -> dict[str, Any]:
        if poster:
            return poster("audio", str(path), caption)
        return telegram.send_audio(token, chat_id, path, caption=caption)

    def send_photo_fn(path: Path, caption: str) -> dict[str, Any]:
        if poster:
            return poster("photo", str(path), caption)
        # sendDocument keeps EXIF/orientation; works for jpg stills
        return telegram.send_document(token, chat_id, path, caption=caption)

    def send_text_fn(msg: str, reply_to: int | None) -> dict[str, Any]:
        if poster:
            return poster("text", msg, reply_to)
        return telegram.send_message(token, chat_id, msg, reply_to=reply_to)

    audio_mid = None
    if audio_path is not None:
        raw = send_audio_fn(audio_path, cap)
        audio_mid = _mid(raw) if isinstance(raw, dict) else None
        if audio_mid:
            ids.append(audio_mid)
            _touch()
        elif isinstance(raw, dict) and raw.get("ok") is False and poster is None:
            print(f"report-cast audio fail {kind} {raw.get('description')}", flush=True)

    photo_mid = audio_mid
    if photo_path is not None:
        raw = send_photo_fn(photo_path, cap if audio_mid is None else "Rear Shed panels")
        photo_mid = _mid(raw) if isinstance(raw, dict) else photo_mid
        if photo_mid and photo_mid not in ids:
            ids.append(photo_mid)
            _touch()
        elif isinstance(raw, dict) and raw.get("ok") is False and poster is None:
            print(f"report-cast photo fail {kind} {raw.get('description')}", flush=True)

    if body:
        raw = send_text_fn(body, photo_mid)
        text_mid = _mid(raw) if isinstance(raw, dict) else None
        if text_mid:
            ids.append(text_mid)
            _touch()
        elif poster is not None:
            ids.append(0)

    if not ids and poster is None:
        return {"ok": False, "detail": "send_failed", "kind": kind}

    for mid in ids:
        _remember(data, mid if mid else None, kind, digest)
    last[kind] = digest
    _save(data)
    print(
        f"report-cast kind={kind} sent={len(ids)} audio={bool(audio_path)} photo={bool(photo_path)}",
        flush=True,
    )
    return {"ok": True, "kind": kind, "ids": ids, "skipped": False}


def maybe_note(
    cfg: Config,
    chat_id: int | str,
    *,
    reply_id: int | None,
    text: str,
    from_id: int | None,
    username: str | None,
    display: str,
    note_id: int | None = None,
) -> bool:
    row = lookup(reply_id)
    if not row:
        return False
    note = (text or "").strip()
    if not note:
        return False
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    rec = {
        "ts": int(time.time()),
        "kind": row.get("kind"),
        "reply_to": reply_id,
        "from_id": from_id,
        "username": (username or "").lstrip("@"),
        "display": display,
        "text": note[:4000],
        "note_id": note_id,
    }
    with NOTES.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
    kind = str(row.get("kind") or "report")
    voice = poster_voice(kind)
    ack = sanitize.sanitize_outbound(
        f"Logged against {title_for(kind)}. I'll use that on the next pass.",
        voice=voice,
    )
    telegram.send_message(cfg.token_for(voice), chat_id, ack, reply_to=note_id)
    _touch()
    print(f"report-note kind={kind} from={display!r}", flush=True)
    return True
