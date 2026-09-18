"""30-minute desk conclusions — stop re-lecturing the same facts.

After the team answers a topic, we seal a short sticky note. A later ask in the
same chat (any human or agent) gets one pointer reply on *their* message that
cites what already stands, instead of three fresh weather dumps.
"""
from __future__ import annotations

import hashlib
import json
import re
import time
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "conclusions.json"
TTL_S = 30 * 60
MAX_ITEMS = 40
SUMMARY_CLIP = 280
ASK_CLIP = 160

_STOP = frozenset(
    """
    a an the and or but to of in on for with about what whats what's how who
    when where why is are was were be been being do does did doing this that
    these those it its it's you your we our they them hey hi hello guys team
    folks everyone everybody please just like really very so too also still
    looking look lookin gonna going get got can could would should will
    """.split()
)

_BUCKETS: list[tuple[str, tuple[str, ...]]] = [
    (
        "weather",
        (
            "weather",
            "forecast",
            "rain",
            "storm",
            "thunder",
            "flood",
            "nws",
            "wind",
            "tomorrow",
            "tonight",
            "humidity",
            "temperature",
            "shower",
        ),
    ),
    (
        "kilauea",
        ("kilauea", "kīlauea", "volcano", "hvo", "erupt", "lava", "vent"),
    ),
    (
        "storm",
        ("hurricane", "cyclone", "typhoon", "dujuan", "pacific basin", "nmi", "bearing"),
    ),
    (
        "ecoflow",
        ("ecoflow", "delta", "river", "solar", "battery", "watts", "generator"),
    ),
    (
        "host",
        ("cpu", "ram", "npu", "ollama", "fastflow", "host", "disk", "uptime"),
    ),
    (
        "skills",
        ("skill", "skills", "desk files", "new skills", "catalog"),
    ),
    (
        "minecraft",
        ("minecraft", "rootmc", "in-game"),
    ),
]


def _now() -> int:
    return int(time.time())


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"items": [], "drafts": {}}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"items": [], "drafts": {}}
    if not isinstance(data, dict):
        return {"items": [], "drafts": {}}
    if not isinstance(data.get("items"), list):
        data["items"] = []
    if not isinstance(data.get("drafts"), dict):
        data["drafts"] = {}
    return data


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    now = _now()
    items = [
        x
        for x in (data.get("items") or [])
        if isinstance(x, dict) and int(x.get("expires") or 0) > now
    ]
    data = dict(data)
    data["items"] = items[-MAX_ITEMS:]
    data["updated"] = now
    drafts = data.get("drafts") if isinstance(data.get("drafts"), dict) else {}
    # Drop drafts older than TTL too
    data["drafts"] = {
        k: v
        for k, v in drafts.items()
        if isinstance(v, dict) and now - int(v.get("ts") or 0) < TTL_S
    }
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def _fold(text: str) -> str:
    s = (text or "").lower().replace("ī", "i").replace("ʻ", "").replace("'", "")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def _content_words(text: str) -> list[str]:
    folded = _fold(text)
    words = re.findall(r"[a-z0-9āēīōū]{3,}", folded)
    return [w for w in words if w not in _STOP]


def topic_key(text: str) -> str:
    """Stable bucket + fingerprint so 'tomorrow weather' matches a later weather ask."""
    folded = _fold(text)
    bucket = "general"
    for name, kws in _BUCKETS:
        if any(k in folded for k in kws):
            bucket = name
            break
    words = _content_words(text)
    if bucket != "general":
        bucket_kws: set[str] = set()
        for name, kws in _BUCKETS:
            if name == bucket:
                bucket_kws = set(kws)
                break
        pinned = [w for w in words if any(w in k or k in w for k in bucket_kws)]
        rest = [w for w in words if w not in pinned]
        words = (pinned + rest)[:10]
    else:
        words = words[:10]
    stem = " ".join(words) if words else folded[:80]
    digest = hashlib.sha1(f"{bucket}:{stem}".encode("utf-8")).hexdigest()[:10]
    return f"{bucket}:{digest}"


def _same_topic(a: str, b: str) -> bool:
    """True when asks share a bucket and enough content overlap (or identical key)."""
    if not a or not b:
        return False
    if a == b:
        return True
    ba, _, _ = a.partition(":")
    bb, _, _ = b.partition(":")
    if ba != bb or ba == "general":
        # general only matches exact key
        return a == b
    # Same desk lane within the TTL window counts as the same conversation.
    return ba == bb


def find(chat_id: int | str, text: str) -> dict[str, Any] | None:
    """Newest unexpired seal in this chat that matches the ask's topic."""
    cid = str(chat_id)
    key = topic_key(text)
    now = _now()
    data = _load()
    best: dict[str, Any] | None = None
    for x in data.get("items") or []:
        if not isinstance(x, dict):
            continue
        if str(x.get("chat_id") or "") != cid:
            continue
        if int(x.get("expires") or 0) <= now:
            continue
        if not _same_topic(str(x.get("topic_key") or ""), key):
            continue
        if best is None or int(x.get("ts") or 0) > int(best.get("ts") or 0):
            best = x
    return best


def should_pointer(chat_id: int | str, text: str) -> dict[str, Any] | None:
    """Return a seal when we should skip a full team re-lecture."""
    hit = find(chat_id, text)
    if not hit:
        return None
    folded = _fold(text)
    # Pure greetings keep the full hello round — do not sticky-note them.
    if re.fullmatch(
        r"(?:hey|hi|hello|yo|good morning|good afternoon|good evening)"
        r"(?:\s+(?:guys|team|folks|everyone|everybody|all))?[.!?]?",
        folded,
    ):
        return None
    return hit


def pointer_text(
    item: dict[str, Any],
    *,
    voice: str,
    display: str,
) -> str:
    """One creative sentence — reply on the new ask, cite the sealed note."""
    who = (display or "friend").strip() or "friend"
    summary = str(item.get("summary") or "").strip() or "what we already posted"
    lead = str(item.get("lead_voice") or "ava").capitalize()
    age_m = max(1, int((_now() - int(item.get("ts") or _now())) / 60))
    ago = f"{age_m} min ago" if age_m < 60 else "earlier"
    v = (voice or "ava").lower()
    templates = {
        "ava": (
            f"{who} — we already settled this {ago}: {summary} "
            f"Same note stands; say if something new changed."
        ),
        "bruce": (
            f"{who}, pointing back to the desk conclusion {ago} "
            f"({lead} led): {summary} No fresh ops delta since then."
        ),
        "carly": (
            f"{who}: still sealed from {ago} — {summary} "
            f"I am not re-running the same risk brief unless the facts moved."
        ),
    }
    body = templates.get(v) or templates["ava"]
    if len(body) > 500:
        body = body[:499] + "…"
    return body


def prompt_block(chat_id: int | str, text: str = "", *, cap: int = 700) -> str:
    """Inject sealed stickies so a full reply still refuses to re-spam."""
    cid = str(chat_id)
    now = _now()
    data = _load()
    lines: list[str] = []
    ask_key = topic_key(text) if text else ""
    for x in data.get("items") or []:
        if not isinstance(x, dict):
            continue
        if str(x.get("chat_id") or "") != cid:
            continue
        if int(x.get("expires") or 0) <= now:
            continue
        if ask_key and not _same_topic(str(x.get("topic_key") or ""), ask_key):
            # Still list nearby seals lightly when no text filter — skip mismatch when filtered
            continue
        bucket = str(x.get("bucket") or "?")
        summary = str(x.get("summary") or "")[:160]
        lead = str(x.get("lead_voice") or "ava")
        lines.append(f"- [{bucket}] {lead}: {summary}")
    if not lines and not text:
        # Unfiltered recent seals for this chat
        for x in data.get("items") or []:
            if not isinstance(x, dict):
                continue
            if str(x.get("chat_id") or "") != cid:
                continue
            if int(x.get("expires") or 0) <= now:
                continue
            bucket = str(x.get("bucket") or "?")
            summary = str(x.get("summary") or "")[:160]
            lead = str(x.get("lead_voice") or "ava")
            lines.append(f"- [{bucket}] {lead}: {summary}")
            if len(lines) >= 6:
                break
    if not lines:
        return ""
    blob = (
        "Sealed desk conclusions (last 30 min) — do not re-lecture these. "
        "If they ask again, point back in one sentence:\n" + "\n".join(lines[-6:])
    )
    return blob[:cap]


def _draft_key(chat_id: int | str, ask: str) -> str:
    return f"{chat_id}:{topic_key(ask)}"


def observe_reply(
    *,
    chat_id: int | str,
    ask: str,
    voice: str,
    text: str,
    message_id: int | None = None,
) -> None:
    """Accumulate team lines toward a seal (does not publish yet)."""
    body = re.sub(r"\s+", " ", (text or "").strip())
    if len(body) < 20:
        return
    if body.lower() in {"pass", "skip", "no add", "nothing to add"}:
        return
    if body.lower().startswith("glitch"):
        return
    ask_s = (ask or "").strip()
    if len(ask_s) < 6:
        return
    data = _load()
    drafts = data.setdefault("drafts", {})
    dk = _draft_key(chat_id, ask_s)
    row = drafts.get(dk) if isinstance(drafts.get(dk), dict) else None
    if not row:
        row = {
            "chat_id": str(chat_id),
            "ask": ask_s[:ASK_CLIP],
            "topic_key": topic_key(ask_s),
            "bucket": topic_key(ask_s).split(":")[0],
            "lines": [],
            "ts": _now(),
        }
    lines = row.get("lines") if isinstance(row.get("lines"), list) else []
    lines.append(
        {
            "voice": (voice or "?").lower(),
            "text": body[:SUMMARY_CLIP],
            "message_id": message_id,
            "ts": _now(),
        }
    )
    row["lines"] = lines[-6:]
    row["ts"] = _now()
    drafts[dk] = row
    data["drafts"] = drafts
    _save(data)


def seal(
    *,
    chat_id: int | str,
    ask: str,
    voice: str | None = None,
    message_id: int | None = None,
) -> dict[str, Any] | None:
    """Publish a 30-minute sticky from the draft (or a single line)."""
    ask_s = (ask or "").strip()
    if len(ask_s) < 6:
        return None
    data = _load()
    drafts = data.get("drafts") if isinstance(data.get("drafts"), dict) else {}
    dk = _draft_key(chat_id, ask_s)
    row = drafts.get(dk) if isinstance(drafts.get(dk), dict) else None
    lines = list(row.get("lines") or []) if row else []
    if not lines:
        return None
    # Prefer Ava's line for the public sticky; else first speaker.
    lead = "ava"
    summary = ""
    anchor = message_id
    by_voice = {str(x.get("voice")): x for x in lines if isinstance(x, dict)}
    pick = by_voice.get("ava") or lines[0]
    lead = str(pick.get("voice") or voice or "ava")
    summary = str(pick.get("text") or "").strip()
    anchor = pick.get("message_id") or message_id
    # Fold one extra voice fact if it adds a distinct clause
    for other in lines:
        if not isinstance(other, dict):
            continue
        if str(other.get("voice")) == lead:
            continue
        extra = str(other.get("text") or "").strip()
        if extra and extra[:40].lower() not in summary.lower():
            clip = extra[:120]
            if len(summary) + len(clip) < SUMMARY_CLIP:
                summary = f"{summary} / {clip}"
            break
    now = _now()
    item = {
        "chat_id": str(chat_id),
        "ask": ask_s[:ASK_CLIP],
        "topic_key": topic_key(ask_s),
        "bucket": topic_key(ask_s).split(":")[0],
        "summary": summary[:SUMMARY_CLIP],
        "lead_voice": lead,
        "anchor_message_id": anchor,
        "voices": sorted({str(x.get("voice")) for x in lines if x.get("voice")}),
        "ts": now,
        "expires": now + TTL_S,
    }
    items = [x for x in (data.get("items") or []) if isinstance(x, dict)]
    # Replace same-topic seal in this chat
    items = [
        x
        for x in items
        if not (
            str(x.get("chat_id")) == str(chat_id)
            and _same_topic(str(x.get("topic_key") or ""), str(item["topic_key"]))
        )
    ]
    items.append(item)
    data["items"] = items
    if dk in drafts:
        del drafts[dk]
    data["drafts"] = drafts
    _save(data)
    print(
        f"conclusion-seal bucket={item['bucket']} lead={lead} ttl={TTL_S}s",
        flush=True,
    )
    return item


def draft_block(chat_id: int | str, ask: str, *, cap: int = 500) -> str:
    """Lines already spoken this round toward a seal — teammates must not re-dump them."""
    ask_s = (ask or "").strip()
    if len(ask_s) < 6:
        return ""
    data = _load()
    drafts = data.get("drafts") if isinstance(data.get("drafts"), dict) else {}
    row = drafts.get(_draft_key(chat_id, ask_s))
    if not isinstance(row, dict):
        return ""
    if str(row.get("chat_id") or "") != str(chat_id):
        return ""
    lines = row.get("lines") if isinstance(row.get("lines"), list) else []
    if not lines:
        return ""
    bits = []
    for x in lines[-4:]:
        if not isinstance(x, dict):
            continue
        who = str(x.get("voice") or "?")
        text = str(x.get("text") or "")[:140]
        if text:
            bits.append(f"- {who}: {text}")
    if not bits:
        return ""
    blob = (
        "Teammates already said this round (add only a new angle, or PASS — do not restate):\n"
        + "\n".join(bits)
    )
    return blob[:cap]


def covers(chat_id: int | str, text: str) -> bool:
    return find(chat_id, text) is not None
