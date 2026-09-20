"""Daily ask/answer memory for humans and agents — stop re-asking."""
from __future__ import annotations

import hashlib
import json
import re
import time
from typing import Any
from zoneinfo import ZoneInfo

from .config import CONFIG_DIR

HST = ZoneInfo("Pacific/Honolulu")
PATH = CONFIG_DIR / "responses-today.json"
MAX_ITEMS = 80
Q_CLIP = 180
ANS_CLIP = 220

AGENT_VOICES = ("ava", "bruce", "carly")
NAME_TO_VOICE = {
    "ava": "ava",
    "ava ivy": "ava",
    "bruce": "bruce",
    "bruce monitor": "bruce",
    "carly": "carly",
    "carly mal": "carly",
    "carla": "carly",
}

# "Ava, did you …?" / "Hey Bruce — can you …?"
ASK_PAT = re.compile(
    r"(?is)(?:^|(?<=[.!?\n])\s*)(?:(?:and|also)\s+)?"
    r"(?:hey\s+|hi\s+|ok\s+|okay\s+)?"
    r"(ava(?:\s+ivy)?|bruce(?:\s+monitor)?|carly(?:\s+mal)?|carla)\s*[,:\-–—]?\s+"
    r"([^?\n]{6,200}\?)"
)
# Also catch trailing vocative: "… right, Ava?"
ASK_TRAIL = re.compile(
    r"(?is)([^?\n]{8,160}\?)\s*(?:ava|bruce|carly|carla)\s*[.!]?\s*$"
)


def _now() -> int:
    return int(time.time())


def _today() -> str:
    return time.strftime("%Y-%m-%d", time.localtime())  # machine is HST


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"date": _today(), "items": []}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"date": _today(), "items": []}
    if not isinstance(data, dict):
        return {"date": _today(), "items": []}
    if str(data.get("date") or "") != _today():
        return {"date": _today(), "items": []}
    items = data.get("items")
    if not isinstance(items, list):
        data["items"] = []
    return data


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["date"] = _today()
    data["updated"] = _now()
    items = [x for x in (data.get("items") or []) if isinstance(x, dict)]
    data["items"] = items[-MAX_ITEMS:]
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def q_key(text: str) -> str:
    s = re.sub(r"\s+", " ", (text or "").strip().lower())
    s = re.sub(r"[^\w\s?]", "", s)
    return hashlib.sha1(s.encode("utf-8")).hexdigest()[:12]


def _norm_voice(name: str) -> str | None:
    low = re.sub(r"\s+", " ", (name or "").strip().lower())
    return NAME_TO_VOICE.get(low)


def extract_asks(text: str, *, from_id: str) -> list[dict[str, str]]:
    """Pull question clips directed at agents from outbound text."""
    t = (text or "").strip()
    if not t:
        return []
    out: list[dict[str, str]] = []
    seen: set[str] = set()
    for m in ASK_PAT.finditer(t):
        to = _norm_voice(m.group(1) or "")
        if not to or to == from_id:
            continue
        q = re.sub(r"\s+", " ", (m.group(2) or "").strip())
        if len(q) < 6:
            continue
        key = q_key(f"{to}:{q}")
        if key in seen:
            continue
        seen.add(key)
        out.append({"to": to, "q": q[:Q_CLIP], "key": key})
    return out


def note_asks(from_id: str, text: str) -> list[dict[str, Any]]:
    """Record new asks from an agent or human. Returns newly stored items."""
    asks = extract_asks(text, from_id=str(from_id))
    if not asks:
        return []
    data = _load()
    items = [x for x in (data.get("items") or []) if isinstance(x, dict)]
    existing = {
        (str(x.get("from")), str(x.get("to")), str(x.get("key")))
        for x in items
        if str(x.get("kind") or "ask") == "ask"
    }
    fresh: list[dict[str, Any]] = []
    for a in asks:
        trip = (str(from_id), a["to"], a["key"])
        if trip in existing:
            continue
        row = {
            "kind": "ask",
            "from": str(from_id),
            "to": a["to"],
            "q": a["q"],
            "key": a["key"],
            "ts": _now(),
            "answered": False,
            "answer": "",
        }
        items.append(row)
        fresh.append(row)
        existing.add(trip)
    data["items"] = items
    if fresh:
        _save(data)
    return fresh


def already_asked(from_id: str, to: str, question: str) -> bool:
    key = q_key(f"{to}:{question}")
    data = _load()
    for x in data.get("items") or []:
        if not isinstance(x, dict):
            continue
        if str(x.get("kind") or "ask") != "ask":
            continue
        if str(x.get("from")) == str(from_id) and str(x.get("to")) == to and str(x.get("key")) == key:
            return True
        # Same question already asked by anyone to this target today
        if str(x.get("to")) == to and str(x.get("key")) == key:
            return True
    return False


def open_asks_for(voice: str) -> list[dict[str, Any]]:
    v = (voice or "").lower()
    data = _load()
    out: list[dict[str, Any]] = []
    for x in data.get("items") or []:
        if not isinstance(x, dict):
            continue
        if str(x.get("kind") or "ask") != "ask":
            continue
        if str(x.get("to")) != v:
            continue
        if x.get("answered"):
            continue
        out.append(x)
    return out[-12:]


def note_answer(voice: str, text: str) -> int:
    """Mark open asks to this voice as answered when they speak a real reply."""
    v = (voice or "").lower()
    body = re.sub(r"\s+", " ", (text or "").strip())
    if len(body) < 12:
        return 0
    # PASS / empty does not count
    if body.lower() in {"pass", "skip", "no add", "nothing to add"}:
        return 0
    data = _load()
    items = [x for x in (data.get("items") or []) if isinstance(x, dict)]
    n = 0
    clip = body[:ANS_CLIP]
    for x in items:
        if str(x.get("kind") or "ask") != "ask":
            continue
        if str(x.get("to")) != v or x.get("answered"):
            continue
        x["answered"] = True
        x["answer"] = clip
        x["answered_ts"] = _now()
        n += 1
    if n:
        data["items"] = items
        _save(data)
    return n


def answered_block(voice: str, *, cap: int = 500) -> str:
    """Questions this voice (or anyone) already got answers to today — do not re-ask."""
    v = (voice or "").lower()
    data = _load()
    lines: list[str] = []
    for x in data.get("items") or []:
        if not isinstance(x, dict):
            continue
        if str(x.get("kind") or "ask") != "ask":
            continue
        if not x.get("answered"):
            continue
        # Relevant if this voice asked it, or it was to someone this voice might ping
        if str(x.get("from")) != v and str(x.get("to")) not in AGENT_VOICES:
            continue
        who = str(x.get("to"))
        q = str(x.get("q") or "")[:100]
        ans = str(x.get("answer") or "")[:100]
        lines.append(f"- To {who}: {q} → {ans}")
    if not lines:
        return ""
    blob = "Already answered today (do not re-ask):\n" + "\n".join(lines[-8:])
    return blob[:cap]


def open_block(voice: str, *, cap: int = 500) -> str:
    """Open questions directed at this speaker — answer them first."""
    opens = open_asks_for(voice)
    if not opens:
        return ""
    lines = ["Open questions for you — answer these before new ones:"]
    for x in opens[-6:]:
        fr = str(x.get("from") or "?")
        q = str(x.get("q") or "")[:120]
        lines.append(f"- From {fr}: {q}")
    return "\n".join(lines)[:cap]


def prompt_block(voice: str, *, cap: int = 700) -> str:
    parts = [open_block(voice, cap=cap // 2), answered_block(voice, cap=cap // 2)]
    blob = "\n".join(p for p in parts if p).strip()
    return blob[:cap]


def follow_targets(text: str, *, from_voice: str) -> list[str]:
    """Who should get a follow-up turn from this utterance."""
    asks = extract_asks(text, from_id=from_voice)
    targets: list[str] = []
    for a in asks:
        to = a["to"]
        if to == from_voice:
            continue
        # Skip if this exact ask was already answered today
        data = _load()
        skip = False
        for x in data.get("items") or []:
            if not isinstance(x, dict):
                continue
            if str(x.get("key")) == a["key"] and x.get("answered"):
                skip = True
                break
        if skip:
            continue
        if to not in targets:
            targets.append(to)
    return targets
