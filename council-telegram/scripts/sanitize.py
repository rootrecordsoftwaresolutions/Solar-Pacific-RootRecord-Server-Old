"""Outbound sanitizer — strip model junk before any Telegram send of generated text."""
from __future__ import annotations

import json
import re

from apps.core.services.speech_scrub import scrub_speech

FALLBACK = "glitch — say that again?"

FILE_BLOCK = re.compile(
    r"<<<FILE\s+path=\"[^\"]*\"\s*>>>[\s\S]*?<<<END_FILE>>>",
    re.IGNORECASE,
)
FILE_OPEN = re.compile(r"<<<FILE\b[^>]*>>>", re.IGNORECASE)
FILE_END = re.compile(r"<<<END_FILE>>>", re.IGNORECASE)
SKILL_MARK = re.compile(r"<<<SKILL\b[^>]*>>>", re.IGNORECASE)
JUDGE_MARK = re.compile(r"<<<JUDGE[\s\S]*?(?:>>>|>)", re.IGNORECASE)
JUDGE_ANY = re.compile(
    r"(?is)(?:<<<\s*)?JUDGE\s+delta\s*=\s*[+-]?\d+\s*"
    r"(?:reason\s*=\s*[^>\n]*)?(?:\s*>>>|\s*>)?"
)
WRAP_LEAK = re.compile(
    r"(?im)^\s*(?:Cursor self-repair is OFF.*|Local Ollama only\..*|"
    r"Code implementer is off.*|Stay on-device\..*|"
    r"Conclude open threads\..*|Do not start a new architecture saga\..*|"
    r"Do not invent community programs\..*)\s*$"
)
OLLAMA_LEAK = re.compile(r"(?im)^.*\[ollama (?:error|unreachable):[^\]]*\].*\s*$")
PERSON_MARK = re.compile(
    r"<<<(?:NOTE|FEATURE|FORGET|ALIAS|GOAL|SKILLIDEA|OPSFIX|RECIPE|PANTRY)\b[^>]*>>>", re.IGNORECASE
)
ANY_CHEVRON = re.compile(r"<<<[^>\n]{0,240}>>>")
HTML_TAG = re.compile(r"</?[a-zA-Z][^>]*>")
CODE_FENCE = re.compile(r"```[\s\S]*?```")
LEADING_GT = re.compile(r"^>{2,}\s?", re.MULTILINE)
AXIS_LINE = re.compile(
    r"^\s*(?:warmth|sting|dryness|concern|spark|pride|engagement|steadiness|"
    r"patience|grump|vigilance|trust_room|respect)\s*[=:]\s*\d+\s*$",
    re.IGNORECASE | re.MULTILINE,
)
AXIS_INLINE = re.compile(
    r"\b(?:warmth|sting|dryness|concern|spark|pride|engagement|steadiness|"
    r"patience|grump|vigilance|trust_room|respect)\s*[=:]?\s*\d+\b",
    re.IGNORECASE,
)
PROGRAMMED_LIKE = re.compile(
    r"[^.!?\n]*\bprogrammed to (?:like|love|want|adore|prefer)\b[^.!?\n]*[.!]?",
    re.IGNORECASE,
)
HEAT_SCORE_TALK = re.compile(
    r"[^.!?\n]*\bheat\b[^.!?\n]*(?:score|level|meter|\d+)[^.!?\n]*[.!]?",
    re.IGNORECASE,
)
OTHER_TRUST_TALK = re.compile(
    r"[^.!?\n]*(?:'s|’s)\s+trust\b[^.!?\n]*[.!]?"
    r"|[^.!?\n]*\btrust(?:\s+score)?\s+(?:for|of)\s+\S+[^.!?\n]*[.!]?"
    r"|[^.!?\n]*\b(?:trust|heat)\s*(?:score)?\s*(?:is|=|:)\s*\d+[^.!?\n]*[.!]?",
    re.IGNORECASE,
)
OPERATOR_NAME = re.compile(r"\bAlexander\b", re.IGNORECASE)
GOSSIP_REFUSE = "I don't brief one person on another. That's theirs to tell."
OPERATOR_GAP = re.compile(
    r"(?i)as for,?\s*(?:he'?s|she'?s|they'?re)\s+a different[^.!?\n]*[.!]?"
    r"|[^.!?\n]*we don'?t talk about (?:him|her|them)(?:\s+here)?[^.!?\n]*[.!]?",
)
SELF_LEAD = {
    "ava": re.compile(
        r"^(?:hey\s+|hi\s+)?(?:@?ava(?:\s+ivy)?(?:_bot)?|@avaivy_bot)\s*[,:]\s*",
        re.I,
    ),
    "bruce": re.compile(
        r"^(?:hey\s+|hi\s+)?(?:@?bruce(?:\s+monitor)?(?:_bot)?|@brucemonitor_bot)\s*[,:]\s*",
        re.I,
    ),
    "carly": re.compile(
        r"^(?:hey\s+|hi\s+)?(?:@?carly(?:\s+mal)?(?:_bot)?|@carlymal_bot|@?carla)\s*[,:]\s*",
        re.I,
    ),
}
# Mid-message self-vocative ("… Hey Ava, …") — strip the address, keep the rest.
SELF_VOCATIVE = {
    "ava": re.compile(
        r"(?:^|(?<=[.!?\n])\s*)(?:hey\s+|hi\s+)?(?:ava(?:\s+ivy)?)\s*[,:]\s*",
        re.I,
    ),
    "bruce": re.compile(
        r"(?:^|(?<=[.!?\n])\s*)(?:hey\s+|hi\s+)?(?:bruce(?:\s+monitor)?)\s*[,:]\s*",
        re.I,
    ),
    "carly": re.compile(
        r"(?:^|(?<=[.!?\n])\s*)(?:hey\s+|hi\s+)?(?:carly(?:\s+mal)?|carla)\s*[,:]\s*",
        re.I,
    ),
}


def _looks_like_json_blob(text: str) -> bool:
    t = (text or "").strip()
    if len(t) < 2 or t[0] not in "{[":
        return False
    try:
        json.loads(t)
        return True
    except json.JSONDecodeError:
        return False


def sanitize_outbound(
    text: str | None,
    *,
    fallback: str = FALLBACK,
    voice: str | None = None,
    allow_operator_name: bool = False,
    forbid_names: set[str] | None = None,
) -> str:
    """Fail closed: never send FILE markers, skill tokens, axis dumps, or raw JSON."""
    clean = (text or "").replace("\r\n", "\n")
    clean = FILE_BLOCK.sub("", clean)
    clean = FILE_OPEN.sub("", clean)
    clean = FILE_END.sub("", clean)
    clean = SKILL_MARK.sub("", clean)
    clean = JUDGE_MARK.sub("", clean)
    clean = JUDGE_ANY.sub("", clean)
    clean = WRAP_LEAK.sub("", clean)
    clean = OLLAMA_LEAK.sub("", clean)
    clean = scrub_speech(clean)
    clean = PERSON_MARK.sub("", clean)
    clean = ANY_CHEVRON.sub("", clean)
    clean = CODE_FENCE.sub("", clean)
    clean = HTML_TAG.sub("", clean)
    clean = LEADING_GT.sub("", clean)
    clean = AXIS_LINE.sub("", clean)
    clean = AXIS_INLINE.sub("", clean)
    clean = PROGRAMMED_LIKE.sub("", clean)
    clean = HEAT_SCORE_TALK.sub("", clean)
    clean = OTHER_TRUST_TALK.sub("", clean)
    if not allow_operator_name:
        clean = OPERATOR_NAME.sub("", clean)
        clean = OPERATOR_GAP.sub("", clean)
        clean = re.sub(r" +", " ", clean)
        clean = re.sub(r"\s+([,.;:!?])", r"\1", clean)
    if forbid_names:
        low = clean.lower()
        for name in forbid_names:
            if name and re.search(rf"\b{re.escape(name)}\b", low):
                return GOSSIP_REFUSE
    v = (voice or "").strip().lower()
    if v == "ava":
        clean = re.sub(r"@avaivy_bot\b", "", clean, flags=re.I)
    elif v == "bruce":
        clean = re.sub(r"@brucemonitor_bot\b", "", clean, flags=re.I)
    elif v == "carly":
        clean = re.sub(r"@carlymal_bot\b", "", clean, flags=re.I)
    lead = SELF_LEAD.get(v)
    if lead:
        for _ in range(3):
            nxt = lead.sub("", clean, count=1)
            if nxt == clean:
                break
            clean = nxt.lstrip()
    voc = SELF_VOCATIVE.get(v)
    if voc:
        clean = voc.sub("", clean)
    clean = re.sub(r"\n{3,}", "\n\n", clean).strip()
    if not clean or _looks_like_json_blob(clean):
        return fallback
    if v == "bruce" and (clean.count("ʻ") + clean.count("ā") + clean.count("ē") + clean.count("ī") + clean.count("ō") + clean.count("ū")) >= 6:
        return "I'll stay in English. Bruce Monitor"
    if len(clean) > 4000:
        clean = clean[:3990] + "…"
    return clean
