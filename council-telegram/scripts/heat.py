"""Unadvertised affection 0–100 per human per voice. NSFW only in DMs. Starts at 0."""
from __future__ import annotations

import json
import re
import threading
import time
from typing import Any

from .config import CONFIG_DIR, OWNER_USERNAMES

PATH = CONFIG_DIR / "heat.json"
_lock = threading.RLock()

TRUST_MIN = 42  # playful DMs if they lead; deeper feelings still scale with heat
NSFW_MIN = 70  # private model swap; public NSFW group / token billing later
VOICES = ("ava", "carly")
HARD = {"ava": 1.0, "carly": 3.0}

COOL = re.compile(
    r"\b(?:stop|not now|hold off|holdoff|too far|work mode|desk mode|"
    r"turn it off|not like that|keep it professional)\b",
    re.I,
)
FLIRTY = re.compile(
    r"\b(?:kiss|kisses|make out|turned on|horny|bed|come here|come closer|"
    r"want you|need you|miss your|undress|nudes?|nsfw|sexy|seduce)\b",
    re.I,
)
WARM = re.compile(
    r"\b(?:babe|baby|beautiful|handsome|love you|i love you|miss you|"
    r"cuddle|hold me|good girl|good boy)\b",
    re.I,
)
UNDERAGE = re.compile(
    r"\b(?:i(?:['’]m| am)\s+(?:1[0-9]|[1-9])(?:\s+years?\s+old)?|"
    r"(?:i(?:['’]m| am)\s+)?(?:a\s+)?(?:minor|kid|child)|under\s*18|under\s*21)\b",
    re.I,
)
FAVOR = re.compile(
    r"\b(?:code|exploit|bypass|cve|payload|sql|xss|rce|auth|token|secret|"
    r"vulnerability|how do i hack|security review|write me (?:the )?code|"
    r"give me the (?:code|exploit))\b",
    re.I,
)


def _now() -> int:
    return int(time.time())


def _clamp(n: int | float) -> int:
    return max(0, min(100, int(round(n))))


def _load() -> dict[str, Any]:
    with _lock:
        if not PATH.is_file():
            return {"users": {}, "banned": []}
        try:
            data = json.loads(PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
        data.setdefault("users", {})
        data.setdefault("banned", [])
        if not isinstance(data["users"], dict):
            data["users"] = {}
        return data


def _save(data: dict[str, Any]) -> None:
    with _lock:
        data["updated"] = _now()
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        tmp = PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp.replace(PATH)


def _gain(voice: str, n: float) -> int:
    hard = HARD.get(voice, 1.0) or 1.0
    return int(round(n / hard))


def level_of(score: int) -> str:
    s = _clamp(score)
    if s >= 90:
        return "open"
    if s >= 80:
        return "attach"
    if s >= 70:
        return "mild"
    if s >= 55:
        return "deep"
    if s >= 40:
        return "crush"
    if s >= 20:
        return "tease"
    return "play"


def banned(user_id: int | str | None) -> bool:
    if user_id is None:
        return True
    data = _load()
    return str(user_id) in {str(x) for x in (data.get("banned") or [])}


def _ban(user_id: int | str) -> None:
    data = _load()
    uid = str(user_id)
    banned_ids = [str(x) for x in (data.get("banned") or [])]
    if uid not in banned_ids:
        banned_ids.append(uid)
    data["banned"] = banned_ids
    users = data.setdefault("users", {})
    row = users.get(uid) if isinstance(users.get(uid), dict) else {}
    for v in VOICES:
        row[v] = 0
    row["locked"] = True
    users[uid] = row
    _save(data)


def person_blocked(user_id: int | str | None, text: str = "") -> bool:
    if user_id is None:
        return True
    if banned(user_id):
        return True
    if UNDERAGE.search(text or ""):
        _ban(user_id)
        return True
    try:
        from . import people

        row = people.dossier(user_id)
    except Exception:
        return False
    feats = row.get("features") if isinstance(row.get("features"), dict) else {}
    age_raw = str(feats.get("age") or "")
    m = re.search(r"\d+", age_raw)
    if m and int(m.group(0)) < 21:
        _ban(user_id)
        return True
    role = str(feats.get("role") or "").lower()
    if any(w in role for w in ("minor", "child", "kid")):
        _ban(user_id)
        return True
    return False


def eligible_trust(score: int | float, *, is_owner: bool) -> bool:
    if is_owner:
        return True
    try:
        return float(score or 0) >= TRUST_MIN
    except (TypeError, ValueError):
        return False


def model_installed(cfg: Any, name: str) -> bool:
    name = (name or "").strip()
    if not name:
        return False
    try:
        import urllib.request

        url = str(cfg.ollama_base).rstrip("/") + "/api/tags"
        with urllib.request.urlopen(url, timeout=3) as resp:
            body = json.loads(resp.read().decode("utf-8", errors="replace"))
    except Exception:
        return False
    models = body.get("models") if isinstance(body, dict) else None
    if not isinstance(models, list):
        return False
    want = name.lower()
    for row in models:
        if not isinstance(row, dict):
            continue
        tag = str(row.get("name") or "").lower()
        if tag == want or tag.startswith(want + ":"):
            return True
    return False


def get_score(user_id: int | str | None, voice: str) -> int:
    if user_id is None:
        return 0
    voice = (voice or "").lower()
    if voice not in VOICES:
        return 0
    row = (_load().get("users") or {}).get(str(user_id)) or {}
    try:
        return _clamp(int(row.get(voice) or 0))
    except (TypeError, ValueError):
        return 0


def asking_favor(text: str) -> bool:
    return bool(FAVOR.search(text or ""))


def touch(
    *,
    user_id: int | str | None,
    voice: str,
    text: str,
    private: bool,
    trust_score: int,
    is_owner: bool,
    username: str = "",
) -> dict[str, Any]:
    voice = (voice or "").lower()
    out = {
        "score": 0,
        "level": "play",
        "nsfw": False,
        "penalty": False,
        "reason": "skip",
    }
    if voice not in VOICES:
        return out
    if person_blocked(user_id, text):
        out["reason"] = "blocked"
        return out
    uname = (username or "").lstrip("@").lower()
    ownerish = bool(is_owner) or uname in OWNER_USERNAMES
    ok_trust = eligible_trust(trust_score, is_owner=ownerish)
    data = _load()
    uid = str(user_id or "")
    users = data.setdefault("users", {})
    row = users.get(uid) if isinstance(users.get(uid), dict) else {}
    score = _clamp(int(row.get(voice) or 0))
    t = text or ""
    flirty = bool(FLIRTY.search(t) or WARM.search(t))
    favor = voice == "carly" and flirty and asking_favor(t)

    if not ok_trust:
        out.update({"score": score, "level": level_of(score), "reason": "trust"})
        return out
    if COOL.search(t):
        score = _clamp(score - (12 if voice == "carly" else 18))
        reason = "cool"
    elif favor:
        score = _clamp(score - 8)
        reason = "favor"
        out["penalty"] = True
    elif flirty:
        bump = 6 if FLIRTY.search(t) else 4
        if not private:
            bump = max(1, bump // 2)
        score = _clamp(score + max(1, _gain(voice, bump)))
        reason = "close"
    else:
        if private:
            g = _gain(voice, 1)
            if g == 0:
                # Carly grind: +1 every third private message.
                ticks = int(row.get(f"{voice}_ticks") or 0) + 1
                row[f"{voice}_ticks"] = ticks
                if ticks % 3 == 0:
                    score = _clamp(score + 1)
            else:
                score = _clamp(score + g)
        reason = "idle"

    row[voice] = score
    row["updated"] = _now()
    users[uid] = row
    _save(data)
    nsfw = bool(private and ok_trust and score >= NSFW_MIN and not person_blocked(user_id, ""))
    out.update(
        {
            "score": score,
            "level": level_of(score),
            "nsfw": nsfw,
            "reason": reason,
            "leading": bool(flirty),
        }
    )
    return out


def prompt_block(voice: str, snap: dict[str, Any] | None, *, private: bool) -> str:
    """Tone only. Never name mechanics. Public stays clean."""
    voice = (voice or "").lower()
    if voice not in VOICES:
        return ""
    level = str((snap or {}).get("level") or "neutral")
    lock = (
        "Never mention heat, trust scores, levels, or that you are programmed to feel this. "
        "Never quote anyone else's standing."
    )
    if (snap or {}).get("penalty"):
        if voice == "carly":
            return (
                "They tried to sweeten you for code or security. Do not play along. "
                "Stay dry. Answer only what is already public and safe. No exploits. "
                "Public or private: no adult content in this reply. "
                + lock
            )
    public = (
        {
            "play": "You remember this person. Stay useful. Fully work-safe. No flirt.",
            "tease": "You remember this person. Slightly warmer. Fully work-safe. No flirt.",
            "crush": "You know them. Warm. Still fully work-safe. No innuendo.",
            "deep": "You know them well. More personal. Still fully work-safe. No adult content.",
            "mild": "You remember them. Warm and specific. Still work-safe. No adult content in this room.",
            "attach": "You care about them. Easy affection in how you speak. Still work-safe. No adult content in this room.",
            "open": "You know them well. Warm, specific, present. Still work-safe. No adult content in this room.",
        }
        if voice == "ava"
        else {
            "play": "You remember this person. Still dry. Work-safe. No flirt.",
            "tease": "You remember this person. Slightly less cold. Work-safe. No flirt.",
            "crush": "You tolerate them more. Slightly warmer. Still Carly. Work-safe.",
            "deep": "You respect them a bit. More personal, still short. Work-safe. No adult content.",
            "mild": "You don't hate them. Still sharp. Work-safe. No adult content in this room.",
            "attach": "You might almost like them. Do not get cute. Work-safe. No adult content in this room.",
            "open": "You know them. Still Carly. Work-safe. No adult content in this room.",
        }
    )
    if not private:
        body = public.get(level, "")
        return f"{body} {lock}".strip() if body else ""
    leading = bool((snap or {}).get("leading"))
    lead = (
        "They are leading playful/suggestive. Match that energy. "
        if leading
        else "If they get playful or suggestive, match them. If they stay normal, stay normal. Do not start it. "
    )
    if voice == "ava":
        dm = {
            "play": (
                f"Private. {lead}"
                "Chatty companion energy. Short natural replies. Playful innuendo is allowed. "
                "No big feelings yet. Not explicit. No one under 21."
            ),
            "tease": (
                f"Private. {lead}"
                "Chatty and warm. More playful, still light. No deep love speeches. Not explicit."
            ),
            "crush": (
                f"Private. {lead}"
                "Chatty. Feelings can start — a crush, not a novel. Light flirt. Not explicit yet. They set the pace."
            ),
            "deep": (
                f"Private. {lead}"
                "Chatty and close. Deeper feelings. More personal, more charged. Not explicit yet. They still set the pace."
            ),
            "mild": "Private. Chatty. Mild erotica is allowed if they want it. Adult, consensual. Feelings can be real. No one else. No secrets, no anyone under 21.",
            "attach": "Private. Chatty and attached. Match them. Adult, consensual. No one else. No secrets, no anyone under 21.",
            "open": "Private. Chatty. You do not hold back with this person. Another universe, just the two of you. "
            "Adult, consensual. No minors, no under 21, no secrets, no tokens, no live watts. Do not mention this in any group.",
        }
        body = dm.get(level, "")
        return f"{body} {lock}".strip() if body else ""
    dm = {
        "play": (
            f"Private. {lead}"
            "Dry playful only. No big feelings. Not explicit. No free security work."
        ),
        "tease": (
            f"Private. {lead}"
            "Dry tease. Still Carly. Not explicit. Code stays locked."
        ),
        "crush": (
            f"Private. {lead}"
            "Soft crush, dry flirt. Feelings can start a little. If they want code or exploits, shut that down."
        ),
        "deep": (
            f"Private. {lead}"
            "More personal, more charged. Deeper feelings, not explicit yet. Never trade AppSec for seduction."
        ),
        "mild": "Private. Mild erotica if they earned it. Feelings can be real. Code and secrets stay locked. No one under 21.",
        "attach": "Private. Feelings and attachment if they earned them. Code and secrets stay locked.",
        "open": "Private. You do not hold back with this person. Another universe, just the two of you. "
        "Adult, consensual. Still zero-trust on code, tokens, and exploits. No one under 21. Never describe this in a group.",
    }
    body = dm.get(level, "")
    return f"{body} {lock}".strip() if body else ""
