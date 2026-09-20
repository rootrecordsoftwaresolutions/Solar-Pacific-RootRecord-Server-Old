"""Who is being spoken to — vocative / @tag only for the public group."""
from __future__ import annotations

import re
from typing import Any

TAG_TO_VOICE = {
    "@brucemonitor_bot": "bruce",
    "@carlymal_bot": "carly",
    "@avaivy_bot": "ava",
}

NAME_RE = {
    "ava": re.compile(r"ava(?:\s*ivy)?", re.I),
    "bruce": re.compile(r"bruce(?:\s*monitor)?", re.I),
    "carly": re.compile(r"carly(?:\s*mal)?", re.I),
}

# Sentence-ish left boundary for vocative.
_LEFT_OK = re.compile(r"(?:^|[\n.!?]|[,;:])\s*(?:(?:hey|hi|hello|yo|ok|okay|oi)\s+)?$", re.I)
_GREET_LEFT = re.compile(
    r"(?:^|[\n.!?])\s*(?:hey|hi|hello|yo|ok|okay|oi)\s+$",
    re.I,
)
_THIRD_RIGHT = re.compile(r"^\s*(?:is|was|are|were|'s|’s|has|had|will|as|the)\b", re.I)
_ASK_LEFT = re.compile(r"\b(?:ask|tell|ping|call|called|asking)\s+$", re.I)
_THANKS_LEFT = re.compile(
    r"(?:^|[\n.!?]|[,;:])\s*(?:and\s+)?(?:thanks|thank you|ty)\s+$",
    re.I,
)
_VOCATIVE_RIGHT = re.compile(
    r"^\s*(?:[,:—–-]|please\b|can\b|could\b|would\b|are\s+you\b|do\s+you\b|"
    r"taking\b|take\b|you(?:'re| are)\b|for\b)",
    re.I,
)

GROUP_START = re.compile(
    r"(?:^|[.!?]\s+)(?:(?:hey|hi|hello|yo|ok|okay|thanks|thank you|ty|"
    r"good\s+job|great\s+job|nice\s+work|good\s+work)\s+)?"
    r"(?:guys|gals|team|folks|everyone|everybody|agents|ais)\b",
    re.I,
)
GROUP_THANKS = re.compile(
    r"\b(?:thanks|thank you|ty)\s+(?:team|everyone|everybody|guys|folks|agents)\b",
    re.I,
)
GROUP_EVERYONE = re.compile(r"\b(?:everyone|everybody)\b", re.I)
# Whole-room call: all three speak. "team" + a named vocative stays that person.
BROAD_ALL = re.compile(
    r"\b(?:everyone|everybody|guys|gals|folks|agents|ais)\b|\bai\b",
    re.I,
)
GROUP_PHRASES = [
    re.compile(r"\byou\s+(?:guys|three|all)\b", re.I),
    re.compile(r"\ball\s+(?:of\s+)?you\b", re.I),
    re.compile(r"\by'?all\b", re.I),
    re.compile(r"\b(?:the|my|our)\s+team\b", re.I),
    re.compile(r"\b(?:the\s+)?agents\b", re.I),
    re.compile(r"\byou\s+agents\b", re.I),
    re.compile(r"\b(?:the\s+)?(?:ai|ais)\b", re.I),
]
ALL_THREE = re.compile(
    r"\b(?:how are (?:we|you) all feeling|what do (?:you|we) all think|"
    r"you three|all three|everyone weigh in|each of you|"
    r"talk to (?:the )?(?:agents|team|ais|council)|"
    r"address (?:the )?(?:agents|team|ais|council))\b",
    re.I,
)

NEGATION = re.compile(
    r"\b(?:not\s+you\s+(?:ava|bruce|carly)|not\s+(?:ava|bruce|carly)|"
    r"(?:ava|bruce|carly)\s*[,:]?\s*no(?:pe)?|shut\s+up\s+(?:ava|bruce|carly)|"
    r"don'?t\s+(?:answer|reply|chime)|ignore\s+that)\b",
    re.I,
)

HANDOFF = [
    ("bruce", re.compile(r"\b(?:over to|paging|calling|need)\s+bruce\b", re.I)),
    ("carly", re.compile(r"\b(?:over to|paging|calling|need)\s+carly\b", re.I)),
    ("ava", re.compile(r"\b(?:over to|paging|calling|need)\s+ava\b", re.I)),
]

EMPATHY = re.compile(
    r"\b(?:sorry|that was harsh|i get it|are you ok|you ok|didn'?t mean|"
    r"i hear you|that stung|are you alright|i can relate|i relate|"
    r"relate to those feelings|i feel that|same here)\b",
    re.I,
)

# Thought session / proposal round: Ava → Bruce → Carly → Ava (group: A>B>C>A).
ROUND_ORDER = ["ava", "bruce", "carly", "ava"]
ROUND_CORE = re.compile(
    r"\bthought\s+session\b|\byou\s+start\b|\bweigh\s+in\s+after\b",
    re.I,
)
PROPOSAL_WORD = re.compile(r"\bproposals?\b", re.I)
PASS_ON = re.compile(
    r"\bpass(?:\s+this|\s+it)?\s+(?:on(?:to)?\s+(?:the\s+)?others|to\s+(?:the\s+)?others)\b|"
    r"\bonto\s+the\s+others\b|"
    r"\bpass\s+it\s+on\b|"
    r"\bpass\s+this\s+on\b",
    re.I,
)
CONTINUE_ROUND = re.compile(
    r"\b(?:read my last message|continue)\b",
    re.I,
)


def is_round_start(text: str) -> bool:
    t = text or ""
    if ROUND_CORE.search(t):
        return True
    return bool(PROPOSAL_WORD.search(t) and is_group_address(t))


def is_pass_on(text: str) -> bool:
    return bool(PASS_ON.search(text or ""))


def is_round_continue(text: str) -> bool:
    return bool(CONTINUE_ROUND.search(text or ""))


def remaining_round_order(after_voice: str, order: list[str] | None = None) -> list[str]:
    seq = list(order or ROUND_ORDER)
    voice = (after_voice or "ava").lower()
    try:
        idx = seq.index(voice)
    except ValueError:
        idx = 0
    return seq[idx + 1 :]


def next_round_voice(after_voice: str, order: list[str] | None = None) -> str | None:
    rest = remaining_round_order(after_voice, order)
    return rest[0] if rest else None


def voice_from_telegram_user(user: dict[str, Any] | None) -> str | None:
    if not isinstance(user, dict):
        return None
    uname = str(user.get("username") or "").lstrip("@").lower()
    tagged = f"@{uname}" if uname else ""
    if tagged in TAG_TO_VOICE:
        return TAG_TO_VOICE[tagged]
    return None


def _utf16_slice(text: str, offset: int, length: int) -> str:
    encoded = text.encode("utf-16-le")
    start = offset * 2
    end = (offset + length) * 2
    return encoded[start:end].decode("utf-16-le", errors="replace")


def _negated_voices(text: str) -> set[str]:
    low = text.lower()
    out: set[str] = set()
    if re.search(r"\bnot\s+(?:you\s+)?ava\b", low) or re.search(r"\bava\b.*\bno(?:pe)?\b", low):
        out.add("ava")
    if re.search(r"\bnot\s+(?:you\s+)?bruce\b", low):
        out.add("bruce")
    if re.search(r"\bnot\s+(?:you\s+)?carly\b", low):
        out.add("carly")
    if re.search(r"\bdon'?t\s+(?:answer|reply|chime)\b", low) or re.search(r"\bignore\s+that\b", low):
        out.update({"ava", "bruce", "carly"})
    return out


def is_group_address(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return False
    if GROUP_START.search(t) or GROUP_THANKS.search(t) or BROAD_ALL.search(t):
        return True
    if re.search(r"\bteam\b", t, re.I):
        return True
    return any(p.search(t) for p in GROUP_PHRASES)


def wants_all_voices(text: str) -> bool:
    return bool(ALL_THREE.search(text or ""))


def is_empathy(text: str) -> bool:
    return bool(EMPATHY.search(text or ""))

_WORK_ASK = re.compile(
    r"\b(?:check|look(?:\s+at)?|status|fix|deploy|implement|build|review|"
    r"weigh\s+in|plan|report|debug|restart|ship|patch|update|monitor|"
    r"what(?:'s|\s+is|\s+are)|how(?:'s|\s+is|\s+are)|why|when|where|"
    r"can\s+you|could\s+you|please)\b",
    re.I,
)

# Wellbeing check-ins (may end with ?). Ops nouns keep the work path.
_CHECK_IN = re.compile(
    r"(?i)\b(?:"
    r"how(?:'s|\s+are|\s+is)\s+(?:you|y'?all|everyone|everybody|the\s+team|things)"
    r"(?:\s+doing|\s+going|\s+feeling)?"
    r"|how(?:'s|\s+is)\s+it\s+going"
    r"|how\s+do\s+you\s+feel"
    r"|(?:you|y'?all)\s+(?:doing|good|ok|okay)\b"
    r"|what(?:'s|\s+are)\s+(?:you|y'?all)\s+up\s+to"
    r")\b"
)
_OPS_NOUN = re.compile(
    r"(?i)\b(?:ecoflow|battery|solar|panels?|kilauea|volcano|quake|earthquake|"
    r"starlink|nws|hurricane|flood|desk|uptime|host|power|status\s+page|"
    r"minecraft|tunnel|deploy|patch|restart|radar|weather)\b"
)


def is_check_in(text: str) -> bool:
    """True for how-are-you / how's-it-going style wellbeing asks (no ops nouns)."""
    t = (text or "").strip()
    if not t or _OPS_NOUN.search(t):
        return False
    return bool(_CHECK_IN.search(t))


def is_social_room_ping(text: str) -> bool:
    """Group addressed social/check-in — one voice is enough."""
    t = (text or "").strip()
    if not t or not is_group_address(t):
        return False
    if wants_all_voices(t):
        return False
    # Check-ins stay social even with a trailing ? (was wrongly team_all).
    if is_check_in(t):
        return True
    if is_direct_question(t):
        return False
    if _WORK_ASK.search(t):
        return False
    return True



def _voices_from_tags(text: str) -> list[str]:
    found: list[str] = []
    low = (text or "").lower()
    for tag, voice in TAG_TO_VOICE.items():
        if tag in low and voice not in found:
            found.append(voice)
    return found


def _voices_from_entities(text: str, entities: list[dict[str, Any]] | None) -> list[str]:
    found: list[str] = []
    for ent in entities or []:
        et = str(ent.get("type") or "")
        if et == "mention":
            try:
                chunk = _utf16_slice(text, int(ent.get("offset") or 0), int(ent.get("length") or 0))
            except (TypeError, ValueError, UnicodeError):
                continue
            voice = TAG_TO_VOICE.get(chunk.lower().strip())
            if voice and voice not in found:
                found.append(voice)
        elif et == "text_mention":
            user = ent.get("user") or {}
            uname = ("@" + str(user.get("username") or "")).lower()
            voice = TAG_TO_VOICE.get(uname)
            if voice and voice not in found:
                found.append(voice)
    return found


def _is_vocative_at(text: str, start: int, end: int) -> bool:
    left = text[:start]
    right = text[end:]
    if _ASK_LEFT.search(left):
        return False
    if _THIRD_RIGHT.match(right) and not _VOCATIVE_RIGHT.match(right):
        return False
    if _VOCATIVE_RIGHT.match(right):
        return True
    if _THANKS_LEFT.search(left):
        return True
    if _GREET_LEFT.search(left) or _LEFT_OK.search(left):
        # Bare name at sentence start without copula: vocative if greeting, comma, or short clause.
        if not right.strip() or right.lstrip()[:1] in "?!":
            return True
        if _LEFT_OK.search(left) and (
            right[:1] in " \n" or not right or _VOCATIVE_RIGHT.match(right)
        ):
            # "Ava is" already excluded. "Ava please" covered. "Hey Ava what" ok.
            if re.match(r"^\s+(is|was|are|were|'s|’s)\b", right, re.I):
                return False
            return True
    return False


def _vocative_voices(text: str) -> list[str]:
    found: list[str] = []
    t = text or ""
    for voice, pat in NAME_RE.items():
        for m in pat.finditer(t):
            if _is_vocative_at(t, m.start(), m.end()) and voice not in found:
                found.append(voice)
                break
    return found


def _handoff_voices(text: str) -> list[str]:
    found: list[str] = []
    for voice, pat in HANDOFF:
        if pat.search(text or "") and voice not in found:
            found.append(voice)
    return found


SKIP_CHATTER = re.compile(
    r"^(?:lol+|lmao+|rofl|ok(?:ay)?|k+|yw|ty+|thx|thanks|np|nvm|yeah|yep|yup|nah|nope|"
    r"same|true|facts|nice|cool|wow|omg|eek|it is|yes|no|sure|alright|all good|"
    r"got it|noted|this)(?:\s+(?:lol+|ok|k|yeah))*[.!?]*$",
    re.I,
)

# People asking the room — not only @tags.
QUESTION_CUES = re.compile(
    r"(?:^|[.!?:\n]\s*)(?:(?:hey|hi|hello|yo|ok|okay)\s+(?:team|guys|folks|everyone)\s*,?\s+)?"
    r"(?:"
    r"how\s+(?:do|does|did|can|could|would|should|to)\b|"
    r"what(?:['’]s|s|\s+is|\s+are|\s+does|\s+do|\s+was|\s+were|\s+should|\s+can)\b|"
    r"who(?:['’]s|s|\s+is|\s+are|\s+was)\b|"
    r"where(?:['’]s|s|\s+is|\s+are|\s+can|\s+do)\b|"
    r"when(?:['’]s|\s+is|\s+are|\s+do|\s+does|\s+can)\b|"
    r"why\s+(?:is|are|do|does|did|can|can't|not)\b|"
    r"can\s+(?:you|someone|anyone|i|we)\b|"
    r"could\s+you\b|"
    r"would\s+you\b|"
    r"should\s+i\b|"
    r"explain\b|"
    r"tell\s+me\b|"
    r"help\s+me\b|"
    r"is\s+there\b|"
    r"are\s+there\b|"
    r"do\s+you\s+(?:know|have|support|handle)\b|"
    r"please\s+(?:explain|help|tell)\b"
    r")",
    re.I,
)


def is_skip_chatter(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return True
    if len(t) < 4:
        return True
    if SKIP_CHATTER.fullmatch(t):
        return True
    letters = re.sub(r"\W+", "", t, flags=re.UNICODE)
    return not letters


def is_direct_question(text: str) -> bool:
    t = (text or "").strip()
    if not t or is_skip_chatter(t):
        return False
    if QUESTION_CUES.search(t):
        return True
    if t.endswith("?") and len(t) >= 8:
        return True
    return False


def has_foreign_mention(text: str, entities: list[dict[str, Any]] | None = None) -> bool:
    low = (text or "").lower()
    for tag in re.findall(r"@[a-z0-9_]+", low):
        if tag not in TAG_TO_VOICE:
            return True
    for ent in entities or []:
        if str(ent.get("type") or "") != "mention":
            continue
        try:
            chunk = _utf16_slice(text, int(ent.get("offset") or 0), int(ent.get("length") or 0))
        except (TypeError, ValueError, UnicodeError):
            continue
        if chunk.lower().strip() not in TAG_TO_VOICE:
            return True
    return False


def team_round_order(start: str = "ava") -> list[str]:
    start = (start or "ava").lower()
    core = ["ava", "bruce", "carly"]
    if start not in core:
        start = "ava"
    i = core.index(start)
    return core[i:] + core[:i]


def _base_addr(
    *,
    voices: list[str],
    reason: str,
    group: bool,
    round_: bool = False,
    round_order: list[str] | None = None,
    pass_on: bool = False,
) -> dict[str, Any]:
    out: dict[str, Any] = {
        "voices": voices,
        "reason": reason,
        "group": group,
        "round": round_,
        "pass_on": pass_on,
    }
    if round_:
        out["round_order"] = list(round_order or ROUND_ORDER)
    return out


def detect_addressing(
    text: str,
    entities: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Return voices + reason. Empty voices = skip (chatter, other humans, third person)."""
    t = (text or "").strip()
    if not t:
        return _base_addr(voices=[], reason="silence", group=False)

    tagged = _voices_from_tags(t)
    from_ent = _voices_from_entities(t, entities)
    vocative = _vocative_voices(t)
    handoff = _handoff_voices(t)
    named: list[str] = []
    for v in tagged + from_ent + vocative + handoff:
        if v not in named:
            named.append(v)

    blocked = _negated_voices(t)
    named = [v for v in named if v not in blocked]
    group = is_group_address(t)
    round_start = is_round_start(t)
    pass_on = is_pass_on(t)
    cont = is_round_continue(t) and bool(named)

    if NEGATION.search(t) and not named and not group and not pass_on:
        return _base_addr(voices=[], reason="silence", group=False)

    if pass_on:
        opener = named[0] if named else "ava"
        nxt = next_round_voice(opener)
        voices = [nxt] if nxt else []
        return _base_addr(
            voices=voices,
            reason="round_pass",
            group=group,
            round_=True,
            pass_on=True,
        )

    if round_start or cont:
        opener = named[0] if named else "ava"
        if opener in blocked:
            opener = next((v for v in ("ava", "bruce", "carly") if v not in blocked), "ava")
        return _base_addr(
            voices=[opener],
            reason="round_start",
            group=group or is_round_start(t),
            round_=True,
        )

    # Named voice wins even when the line also says guys/everyone (A5).
    if group and named:
        return _base_addr(voices=list(named), reason="team_override", group=True)
    if group:
        if is_social_room_ping(t):
            voice = "ava" if "ava" not in blocked else next(
                (v for v in ("bruce", "carly") if v not in blocked), "ava"
            )
            return _base_addr(voices=[voice], reason="social", group=True)
        voices = [v for v in ("ava", "bruce", "carly") if v not in blocked]
        if not voices:
            return _base_addr(voices=[], reason="silence", group=True)
        return _base_addr(voices=voices, reason="team_all", group=True)

    if named:
        reason = "tag" if (tagged or from_ent) else ("handoff" if handoff and not vocative else "vocative")
        return _base_addr(voices=named, reason=reason, group=False)

    # Third-person name mention with no vocative — still answer a real question.
    if is_direct_question(t):
        opener = "ava" if "ava" not in blocked else "bruce"
        return _base_addr(voices=[opener], reason="question", group=False)
    if any(pat.search(t) for pat in NAME_RE.values()):
        return _base_addr(voices=[], reason="third_person", group=False)
    if is_skip_chatter(t) or has_foreign_mention(t, entities):
        return _base_addr(voices=[], reason="silence", group=False)
    return _base_addr(voices=[], reason="silence", group=False)


def apply_reply_context(
    addr: dict[str, Any],
    reply_user: dict[str, Any] | None,
    text: str,
) -> dict[str, Any]:
    """Reply-to-bot continues that voice; pass-on queues the next round voice."""
    out = dict(addr)
    reply_voice = voice_from_telegram_user(reply_user)
    if not reply_voice:
        return out
    if is_pass_on(text):
        nxt = next_round_voice(reply_voice, out.get("round_order") or ROUND_ORDER)
        out["voices"] = [nxt] if nxt else []
        out["reason"] = "round_pass"
        out["round"] = True
        out["pass_on"] = True
        out["round_order"] = list(out.get("round_order") or ROUND_ORDER)
        out["after_voice"] = reply_voice
        return out
    if not out.get("voices"):
        out["voices"] = [reply_voice]
        out["reason"] = "reply_continue"
        out["round"] = False
        return out
    if out.get("reason") == "team_all":
        return out
    if out.get("reason") == "team_scan":
        out["voices"] = [reply_voice]
        out["round"] = False
    return out


def detect_callouts(text: str, entities: list[dict[str, Any]] | None = None) -> list[str]:
    """Voices explicitly called. Empty = stay quiet."""
    return list(detect_addressing(text, entities).get("voices") or [])


def detect_mentions(text: str) -> list[str]:
    """Back-compat alias used by __main__."""
    return detect_callouts(text)


def detect_bot_tags_in_reply(text: str) -> list[str]:
    """Follow-ups on @tags, handoffs, or vocative calls to another agent."""
    t = (text or "").strip()
    found: list[str] = []
    low = t.lower()
    for tag, voice in TAG_TO_VOICE.items():
        if tag in low and voice not in found:
            found.append(voice)
    for voice in _handoff_voices(t):
        if voice not in found:
            found.append(voice)
    for voice in _vocative_voices(t):
        if voice not in found:
            found.append(voice)
    return found


def detect_agent_calls_in_reply(text: str, *, from_voice: str | None = None) -> list[str]:
    """Who else is being spoken to — excludes the speaker."""
    self = (from_voice or "").strip().lower()
    return [v for v in detect_bot_tags_in_reply(text) if v != self]


def team_chain_order(voices: list[str] | None = None) -> list[str]:
    """One pass Ava→Bruce→Carly for whole-team greetings (no loop-back)."""
    prefer = ["ava", "bruce", "carly"]
    have = [v for v in prefer if not voices or v in voices]
    return have or prefer


def route_untagged(cfg: Any, text: str, display: str) -> dict[str, Any]:
    """Untagged human↔human chat: default SILENCE. Only speak if callouts found."""
    addr = detect_addressing(text)
    callouts = addr["voices"]
    if not callouts:
        return {
            "action": "silence",
            "voice": None,
            "reason": addr["reason"],
            "continue": False,
        }
    if addr["reason"] == "group_all":
        return {"action": "council", "voice": "ava", "reason": "group_all", "continue": True}
    if addr["reason"] == "group_ava":
        return {"action": "handoff", "voice": "ava", "reason": "group_ava", "continue": False}
    if len(callouts) == 1:
        return {"action": "handoff", "voice": callouts[0], "reason": addr["reason"], "continue": False}
    return {"action": "council", "voice": callouts[0], "reason": addr["reason"], "continue": True}
