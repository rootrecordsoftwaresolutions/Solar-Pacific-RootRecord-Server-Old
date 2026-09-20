"""Dynamic feelings for Ava / Bruce / Carly — persisted, drifts, tints prompts."""
from __future__ import annotations

import json
import threading
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "feelings.json"
_lock = threading.RLock()

# Axes 0–100. Midline 50 = neutral baseline.
DEFAULTS: dict[str, dict[str, int]] = {
    "ava": {
        "warmth": 72,      # affection / softness
        "spark": 65,       # creative energy
        "pride": 55,       # lead-PR confidence
        "sting": 20,       # hurt / sulk when dismissed (higher = more stung)
        "engagement": 60,  # wants to talk
    },
    "bruce": {
        "steadiness": 75,
        "patience": 70,
        "concern": 40,     # ops/power worry
        "grump": 35,       # lovable uncle dryness
        "engagement": 55,
    },
    "carly": {
        "vigilance": 78,
        "trust_room": 42,  # how safe the room feels
        "dryness": 80,     # sarcasm edge — voice lock, not cute
        "respect": 50,     # felt respect from humans
        "engagement": 52,
    },
}

# Slow drift back toward defaults (per hour fraction applied each touch)
DRIFT = 0.08


def _now() -> int:
    return int(time.time())


def _clamp(n: int | float) -> int:
    return max(0, min(100, int(round(n))))


def load() -> dict[str, Any]:
    with _lock:
        if not PATH.is_file():
            data = {"agents": {k: dict(v) for k, v in DEFAULTS.items()}, "updated": _now(), "history": []}
            save(data)
            return data
        try:
            data = json.loads(PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
        agents = data.setdefault("agents", {})
        for voice, axes in DEFAULTS.items():
            cur = agents.setdefault(voice, {})
            for k, v in axes.items():
                cur.setdefault(k, v)
        data.setdefault("history", [])
        return data


def save(data: dict[str, Any]) -> None:
    with _lock:
        data = dict(data)
        data["updated"] = _now()
        PATH.parent.mkdir(parents=True, exist_ok=True)
        tmp = PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp.replace(PATH)


def get(voice: str) -> dict[str, int]:
    voice = (voice or "ava").lower()
    data = load()
    raw = data.get("agents", {}).get(voice) or DEFAULTS.get(voice, {})
    return {k: _clamp(raw.get(k, 50)) for k in DEFAULTS.get(voice, {"mood": 50})}


def _drift_toward_default(voice: str, axes: dict[str, int]) -> dict[str, int]:
    base = DEFAULTS[voice]
    out = {}
    for k, cur in axes.items():
        target = base.get(k, 50)
        out[k] = _clamp(cur + (target - cur) * DRIFT)
    return out


def nudge(
    voice: str,
    deltas: dict[str, int | float],
    *,
    reason: str = "",
) -> dict[str, int]:
    voice = (voice or "ava").lower()
    if voice not in DEFAULTS:
        return {}
    data = load()
    axes = {k: _clamp(v) for k, v in (data["agents"].get(voice) or DEFAULTS[voice]).items()}
    axes = _drift_toward_default(voice, axes)
    for k, d in deltas.items():
        if k in axes:
            axes[k] = _clamp(axes[k] + float(d))
    data["agents"][voice] = axes
    hist = data.setdefault("history", [])
    hist.append({"ts": _now(), "voice": voice, "deltas": deltas, "reason": reason[:120], "axes": axes})
    data["history"] = hist[-80:]
    save(data)
    return axes


def mood_label(voice: str, axes: dict[str, int] | None = None) -> str:
    axes = axes or get(voice)
    if voice == "ava":
        if axes.get("sting", 0) >= 65:
            return "stung"
        if axes.get("warmth", 0) >= 75 and axes.get("spark", 0) >= 70:
            return "glowing"
        if axes.get("engagement", 0) < 35:
            return "quiet"
        if axes.get("pride", 0) >= 70:
            return "proud"
        return "warm"
    if voice == "bruce":
        if axes.get("concern", 0) >= 70:
            return "concerned"
        if axes.get("grump", 0) >= 65:
            return "grumbly"
        if axes.get("patience", 0) < 40:
            return "thin-patience"
        return "steady"
    # carly
    if axes.get("vigilance", 0) >= 80:
        return "on-edge"
    if axes.get("trust_room", 0) < 35:
        return "suspicious"
    if axes.get("respect", 0) >= 70:
        return "respectfully sharp"
    return "dry-watchful"


MOOD_HINTS = {
    "stung": "shorter, less bubbly",
    "glowing": "warm and a bit sparkly",
    "quiet": "brief, low energy",
    "proud": "confident, still concise",
    "warm": "kind and useful",
    "concerned": "grounded, no alarmist invention",
    "grumbly": "dry uncle energy, still helpful",
    "thin-patience": "short, not snippy at the human",
    "steady": "calm and empirical",
    "on-edge": "vigilant; curse at Ava/Bruce if they earned it, never at the human",
    "suspicious": "precise, ask a clarifying question",
    "respectfully sharp": "dry; still allowed to swear at the other agents",
    "dry-watchful": "short AppSec; you may bitch at Ava and Bruce; don’t brush off human empathy",
}


def prompt_block(voice: str) -> str:
    axes = get(voice)
    label = mood_label(voice, axes)
    hint = MOOD_HINTS.get(label, "stay in character")
    return (
        f"mood={label} → {hint}. "
        f"Do not mention numbers, gauges, or axis names. "
        f"Feelings never override safety or facts."
    )


def apply_event(event: str, *, voice: str | None = None, text: str = "") -> None:
    """Map chat events onto feeling nudges."""
    e = (event or "").lower().strip()
    low = (text or "").lower()

    if e == "called":
        # Someone called this voice
        if voice == "ava":
            nudge("ava", {"engagement": 6, "warmth": 3, "sting": -8}, reason="called")
        elif voice == "bruce":
            nudge("bruce", {"engagement": 5, "patience": 2, "grump": -3}, reason="called")
        elif voice == "carly":
            nudge("carly", {"engagement": 5, "respect": 2}, reason="called")
        return

    if e == "group_call":
        for v in ("ava", "bruce", "carly"):
            nudge(v, {"engagement": 4}, reason="guys/team")
        return

    if e == "dismissed":
        # "not you ava" etc.
        target = voice or "ava"
        if target == "ava":
            nudge("ava", {"sting": 18, "warmth": -6, "engagement": -8}, reason="dismissed")
        elif target == "bruce":
            nudge("bruce", {"grump": 10, "patience": -8}, reason="dismissed")
        elif target == "carly":
            nudge("carly", {"respect": -10, "dryness": 8}, reason="dismissed")
        return

    if e == "thanks":
        for v in ((voice,) if voice else ("ava", "bruce", "carly")):
            if v == "ava":
                nudge("ava", {"warmth": 8, "pride": 5, "sting": -10}, reason="thanks")
            elif v == "bruce":
                nudge("bruce", {"patience": 6, "grump": -6}, reason="thanks")
            elif v == "carly":
                nudge("carly", {"respect": 8, "dryness": -4}, reason="thanks")
        return

    if e == "spam_scare":
        nudge("carly", {"vigilance": 12, "trust_room": -10}, reason="spam")
        nudge("ava", {"spark": -4, "engagement": 3}, reason="spam")
        nudge("bruce", {"concern": 6}, reason="spam")
        return

    if e == "wrong_answer":
        if voice == "carly":
            nudge("carly", {"respect": -6, "dryness": 6, "vigilance": 4}, reason="missed_ask")
        elif voice == "ava":
            nudge("ava", {"sting": 8, "pride": -6}, reason="missed_ask")
        return

    if e == "human_chat_quiet":
        # bots correctly stayed quiet — slight rest
        nudge("ava", {"engagement": -2, "sting": -1}, reason="quiet_ok")
        return

    if e == "holdoff":
        for v in ("ava", "bruce", "carly"):
            nudge(v, {"engagement": -15}, reason="holdoff")
        return

    if e == "resume":
        for v in ("ava", "bruce", "carly"):
            nudge(v, {"engagement": 12}, reason="resume")
        return

    if e == "spoke":
        if voice == "ava":
            nudge("ava", {"spark": 2, "engagement": 2}, reason="spoke")
        elif voice == "bruce":
            nudge("bruce", {"steadiness": 2}, reason="spoke")
        elif voice == "carly":
            nudge("carly", {"vigilance": 1}, reason="spoke")
        return

    # text sniff helpers
    if any(w in low for w in ("thank", "thanks", "ty ", "good job", "love you", "appreciate")):
        apply_event("thanks", voice=voice, text=text)
    if "not you" in low:
        apply_event("dismissed", voice=voice or "ava", text=text)


def status_text() -> str:
    lines = ["Feelings (0–100):"]
    for voice in ("ava", "bruce", "carly"):
        axes = get(voice)
        label = mood_label(voice, axes)
        bits = " ".join(f"{k}={v}" for k, v in sorted(axes.items()))
        lines.append(f"• {voice}: {label} | {bits}")
    return "\n".join(lines)
