"""Ask if another specialist should DM. Only after the current bot answered."""
from __future__ import annotations

import json
import re
import time
from typing import Any

from . import sanitize, telegram
from .config import CONFIG_DIR, Config

PATH = CONFIG_DIR / "refer.json"
VOICES = ("ava", "bruce", "carly")
HANDLES = {
    "ava": "@avaivy_bot",
    "bruce": "@brucemonitor_bot",
    "carly": "@carlymal_bot",
}
NAMES = {"ava": "Ava", "bruce": "Bruce", "carly": "Carly"}
LANE = {
    "ava": "public voice, brand, community, and design",
    "bruce": "ops, desk reality, philosophy, and academic discussion",
    "carly": "cybersecurity, safety, defence, and strategy",
}
OFFER_RE = re.compile(
    r"<<<OFFER\s+voice\s*=\s*(ava|bruce|carly)\s+topic\s*=\s*([^>]*?)>>>",
    re.I,
)
YES_RE = re.compile(
    r"(?i)^\s*(?:yes|yeah|yep|yup|sure|please|ok|okay|do it|go ahead|"
    r"that(?:'s| is| would be|'d be) (?:great|good|fine)|sounds good|"
    r"have (?:them|him|her|bruce|carly|ava) (?:message|dm|text))"
    r"(?:\s+please)?\s*[.!]?\s*$"
)
NO_RE = re.compile(
    r"(?i)^\s*(?:no|nah|nope|don't|dont|not now|no thanks|no thank you)\b"
)
ADULT = re.compile(
    r"(?i)\b(?:nsfw|sex|sexual|nude|horny|explicit|porn)\b"
)
TTL_S = 6 * 3600


def team_prompt(voice: str, *, private: bool = False) -> str:
    v = (voice or "ava").lower()
    bits = []
    for other in VOICES:
        if other == v:
            continue
        bits.append(f"{NAMES[other]} ({LANE[other]})")
    if private:
        return (
            f"You are {NAMES.get(v, v)} in a private DM. Answer as yourself first. "
            "Do not lecture about public voice, brand PR, or group policy on a check-in. "
            "Do not dodge. Do not change the subject. Do not pitch clubs, meetups, Facebook, or "
            "'go talk to people' unless they asked how to meet humans. "
            "Do not tell them to ask someone else instead of answering. "
            f"If the topic is clearly more {bits[0]} or {bits[1]}, after you have answered, "
            "ask if they want that bot to DM them, and emit hidden "
            "<<<OFFER voice=bruce|carly|ava topic=short phrase>>>. Never offer yourself."
        )
    return (
        "Team specialties: Ava — public voice, brand, community, design. "
        "Bruce — ops, philosophy, academic discussion. "
        "Carly — cybersecurity, safety, defence, strategy. "
        f"You are {NAMES.get(v, v)}. Answer their question yourself first. "
        "Do not dodge. Do not change the subject. Do not pitch clubs, meetups, Facebook, or "
        "'go talk to people' unless they asked how to meet humans. "
        "Do not tell them to ask someone else instead of answering. "
        f"If the topic is clearly more {bits[0]} or {bits[1]}, after you have answered, "
        "ask if they want that bot to DM them, and emit hidden "
        "<<<OFFER voice=bruce|carly|ava topic=short phrase>>>. Never offer yourself."
    )


def _now() -> int:
    return int(time.time())


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"users": {}}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"users": {}}
    if not isinstance(data.get("users"), dict):
        data["users"] = {}
    return data


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def parse_offer(text: str, *, from_voice: str) -> dict[str, str] | None:
    m = OFFER_RE.search(text or "")
    if not m:
        return None
    to = m.group(1).lower()
    topic = re.sub(r"\s+", " ", (m.group(2) or "").strip())[:160]
    if to not in VOICES or to == (from_voice or "").lower():
        return None
    if not topic or ADULT.search(topic):
        return None
    return {"to": to, "topic": topic}


def pending(user_id: int | str) -> dict[str, Any] | None:
    uid = str(user_id)
    row = _load().get("users", {}).get(uid)
    if not isinstance(row, dict):
        return None
    if _now() - int(row.get("ts") or 0) > TTL_S:
        return None
    if row.get("sent"):
        return None
    return row


def note_offer(user_id: int | str | None, raw: str, *, from_voice: str) -> dict[str, Any]:
    if user_id is None:
        return {"ok": False, "detail": "no_user"}
    hit = parse_offer(raw, from_voice=from_voice)
    if not hit:
        return {"ok": False, "detail": "no_tag"}
    data = _load()
    uid = str(user_id)
    prev = data.get("users", {}).get(uid)
    if isinstance(prev, dict) and prev.get("sent") and _now() - int(prev.get("sent_ts") or 0) < 3600:
        if str(prev.get("to")) == hit["to"]:
            return {"ok": False, "detail": "cooldown"}
    data.setdefault("users", {})[uid] = {
        "from": from_voice,
        "to": hit["to"],
        "topic": hit["topic"],
        "ts": _now(),
        "sent": False,
    }
    _save(data)
    return {"ok": True, "to": hit["to"], "topic": hit["topic"]}


def _intro(*, frm: str, to: str, topic: str) -> str:
    who = NAMES.get(frm, frm)
    lane = LANE.get(to, "this")
    return (
        f"{who} told me you two were talking about {topic}. "
        f"I'm {NAMES.get(to, to)} — {lane}. "
        "I'm available if you want to go more in depth on that. Your call."
    )


def _need_start(to: str) -> str:
    handle = HANDLES.get(to, "")
    name = NAMES.get(to, to)
    return (
        f"{name} couldn't reach your DMs yet. Open {handle} and tap Start, "
        "then say yes here again."
    )


def dispatch(cfg: Config, user_id: int | str) -> dict[str, Any]:
    uid = str(user_id)
    row = pending(uid)
    if not row:
        return {"ok": False, "detail": "none"}
    to = str(row.get("to") or "")
    frm = str(row.get("from") or "ava")
    topic = str(row.get("topic") or "that")
    if to not in VOICES:
        return {"ok": False, "detail": "bad_voice"}
    body = sanitize.sanitize_outbound(
        _intro(frm=frm, to=to, topic=topic),
        voice=to,
        allow_operator_name=False,
    )
    res = telegram.send_message(cfg.token_for(to), uid, body[:3500])
    data = _load()
    cur = data.get("users", {}).get(uid)
    if not isinstance(cur, dict):
        return {"ok": False, "detail": "missing"}
    if not res.get("ok"):
        telegram.send_message(
            cfg.token_for(frm if frm in VOICES else "ava"),
            uid,
            sanitize.sanitize_outbound(_need_start(to), voice=frm),
        )
        return {"ok": False, "detail": "need_start"}
    cur["sent"] = True
    cur["sent_ts"] = _now()
    data["users"][uid] = cur
    _save(data)
    print(f"refer-dm from={frm} to={to} user={uid}", flush=True)
    return {"ok": True, "to": to}


def clear(user_id: int | str) -> None:
    data = _load()
    data.setdefault("users", {}).pop(str(user_id), None)
    _save(data)


def maybe_consume(cfg: Config, user_id: int | str | None, text: str) -> str:
    """If they answered an offer, send or drop. Returns sent|cleared|none."""
    if user_id is None:
        return "none"
    if not pending(user_id):
        return "none"
    raw = (text or "").strip()
    if NO_RE.search(raw):
        clear(user_id)
        return "cleared"
    if YES_RE.search(raw) or re.search(
        r"(?i)\b(?:have|let|ask|tell)\s+(?:bruce|carly|ava)\b", raw
    ):
        out = dispatch(cfg, user_id)
        return "sent" if out.get("ok") else "failed"
    return "none"


def pending_prompt(user_id: int | str | None) -> str:
    if user_id is None:
        return ""
    row = pending(user_id)
    if not row:
        return ""
    to = NAMES.get(str(row.get("to")), "them")
    topic = str(row.get("topic") or "that")
    return (
        f"You already offered {to} a DM about {topic}. "
        "If they are saying yes, confirm briefly. If no, drop it. "
        "Still answer any other question first."
    )
