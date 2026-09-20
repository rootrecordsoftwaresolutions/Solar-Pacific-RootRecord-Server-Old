"""Intent classifier — heuristics first; tiny Ollama model is advisory only."""
from __future__ import annotations

import json
import re
from typing import Any

from . import ollama_client
from .config import NUM_PREDICT, Config
from .personas import CLASSIFIER_SYSTEM

INTENTS = frozenset(
    {"ignore", "command", "casual", "council", "implement", "mention_one", "moderation"}
)
VOICE_ALIASES = {
    "ava": "ava",
    "avaivy": "ava",
    "avaivy_bot": "ava",
    "@avaivy_bot": "ava",
    "bruce": "bruce",
    "brucemonitor": "bruce",
    "brucemonitor_bot": "bruce",
    "@brucemonitor_bot": "bruce",
    "carly": "carly",
    "carlymal": "carly",
    "carlymal_bot": "carly",
    "@carlymal_bot": "carly",
}

_CMD_EXACT = frozenset(
    {
        "hold off",
        "holdoff",
        "quiet",
        "stop talking",
        "resume",
        "discussion on",
        "discussion off",
        "you can talk",
        "status",
        "ping",
    }
)


def _extract_json(text: str) -> dict[str, Any] | None:
    text = (text or "").strip()
    if not text:
        return None
    try:
        obj = json.loads(text)
        if isinstance(obj, dict):
            return obj
    except json.JSONDecodeError:
        pass
    m = re.search(r"\{[^{}]*\}", text, re.DOTALL)
    if m:
        try:
            obj = json.loads(m.group(0))
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            return None
    return None


def _looks_like_command(text: str) -> bool:
    low = (text or "").strip().lower()
    return low.startswith("/") or low in _CMD_EXACT


def heuristic(text: str, mode: str) -> dict[str, Any]:
    t = (text or "").strip()
    low = t.lower()
    if not t:
        return {"intent": "ignore", "voice": None, "reason": "empty"}

    if _looks_like_command(t):
        return {"intent": "command", "voice": None, "reason": "slash_or_owner_phrase"}

    mod_words = (
        "spam",
        "spammer",
        "scam",
        "scammer",
        "ban them",
        "kick them",
        "mute them",
        "moderation",
        "remove this",
        "phishing",
    )
    if any(w in low for w in mod_words):
        return {"intent": "moderation", "voice": "ava", "reason": "mod_keyword"}

    for needle, voice in (
        ("@brucemonitor_bot", "bruce"),
        ("@carlymal_bot", "carly"),
        ("@avaivy_bot", "ava"),
    ):
        if needle in low:
            others = sum(
                1
                for n in ("@brucemonitor_bot", "@carlymal_bot", "@avaivy_bot")
                if n in low
            )
            if others == 1:
                return {
                    "intent": "mention_one",
                    "voice": voice,
                    "reason": "single_mention",
                }

    if any(
        k in low
        for k in (
            "implement",
            "build this",
            "write the code",
            "cursor",
            "ship it",
            "code this",
        )
    ):
        return {"intent": "implement", "voice": None, "reason": "implement_words"}

    if any(
        k in low
        for k in (
            "what do you think",
            "council",
            "architecture",
            "design",
            "weigh in",
            "all three",
            "you three",
        )
    ):
        return {"intent": "council", "voice": None, "reason": "council_words"}

    if mode == "council":
        return {"intent": "council", "voice": None, "reason": "mode_council"}
    if mode == "casual":
        return {"intent": "casual", "voice": None, "reason": "mode_casual"}
    return {"intent": "casual", "voice": None, "reason": "default_casual"}


def classify(cfg: Config, text: str, mode: str, ollama_up: bool) -> dict[str, Any]:
    heur = heuristic(text, mode)
    if not ollama_up or heur["intent"] == "command":
        return heur

    # Skip flaky 1.5b for short casual hellos
    words = (text or "").strip().split()
    if heur["intent"] == "casual" and len(words) <= 8:
        return heur
    # Prefer strong heuristics for council/moderation/implement/mention
    if heur["intent"] in {"council", "moderation", "implement", "mention_one"}:
        return heur

    raw = ollama_client.chat(
        cfg,
        cfg.chat_model,
        CLASSIFIER_SYSTEM,
        f"routing_profile={mode}\nuser_message:\n{text}",
        num_predict=NUM_PREDICT["classifier"],
        timeout=60,
        voice="router",
    )
    obj = _extract_json(raw)
    if not obj:
        return heur
    intent = str(obj.get("intent", "")).strip().lower()
    if intent not in INTENTS:
        return heur
    voice = obj.get("voice")
    if voice:
        voice = VOICE_ALIASES.get(str(voice).strip().lower(), str(voice).strip().lower())
        if voice not in ("ava", "bruce", "carly"):
            voice = None

    if intent == "command" and not _looks_like_command(text):
        return heur
    if intent == "mention_one" and voice is None:
        return heur if heur["intent"] != "ignore" else {
            "intent": "casual",
            "voice": "ava",
            "reason": "mention_without_voice",
        }
    return {
        "intent": intent,
        "voice": voice,
        "reason": str(obj.get("reason") or "classifier"),
    }
