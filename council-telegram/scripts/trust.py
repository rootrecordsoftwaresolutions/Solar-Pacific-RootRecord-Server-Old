"""Trust scores for group humans."""
from __future__ import annotations

import json
import math
import re
import time
from pathlib import Path
from typing import Any

from .config import TRUST_PATH

DEFAULT_SCORE = 40
NON_OWNER_MAX_SCORE = 90  # per voice; only the bound operator may be 100
OWNER_SCORE = 100
GAIN_FACTOR = 0.5
LOSS_FACTOR = 1.5
OWNER_BOOTSTRAP_SCORE = 100
GUEST_MIN = 25
CONTRIBUTE_MIN = 40  # average of the three; agents reply to 40+ in group
REQUEST_EXECUTE_MIN = 60
AUTO_EXECUTE_MIN = 95
VOICES = ("ava", "bruce", "carly")
VOICE_EMOJI = {"ava": "🌸", "bruce": "🔧", "carly": "🛡️"}
VOICE_LABEL = {"ava": "Ava", "bruce": "Bruce", "carly": "Carly"}
COMBINED_MAX = OWNER_SCORE * 3


def _as_float(n: Any, default: float = 0.0) -> float:
    try:
        return float(n)
    except (TypeError, ValueError):
        return float(default)


def floor_score(n: Any) -> int:
    return int(math.floor(_as_float(n) + 1e-9))


def _fmt_n(n: Any) -> str:
    x = _as_float(n)
    if abs(x - round(x)) < 1e-9:
        return str(int(round(x)))
    return f"{x:.2f}".rstrip("0").rstrip(".")


JUDGE_STEP = {1: 0.1, 2: 0.2, 3: 0.3}


def judge_applied(delta: int | float) -> float:
    """Map a hidden JUDGE tag to 0.1 / 0.2 / 0.3. Same size good or bad."""
    try:
        d = int(delta)
    except (TypeError, ValueError):
        return 0.0
    if d == 0:
        return 0.0
    mag = min(3, abs(d))
    return math.copysign(float(JUDGE_STEP.get(mag, 0.1)), d)


def voice_from_event(ev: dict[str, Any]) -> str:
    v = str(ev.get("voice") or "").lower()
    if v in VOICES:
        return v
    m = re.search(r"agent:(ava|bruce|carly):", str(ev.get("reason") or ""), re.I)
    return m.group(1).lower() if m else "ava"


def band_label(score: int) -> str:
    """Public ladder names — same bands as apps/council/README.md."""
    try:
        s = int(score)
    except (TypeError, ValueError):
        s = DEFAULT_SCORE
    s = max(0, min(100, s))
    if s >= OWNER_SCORE:
        return "Owner"
    if s >= NON_OWNER_MAX_SCORE:
        return "Peer"
    if s >= 75:
        return "Core"
    if s >= REQUEST_EXECUTE_MIN:
        return "Trusted"
    if s >= DEFAULT_SCORE:
        return "Member"
    if s >= GUEST_MIN:
        return "Guest"
    return "Watched"


def band_emoji(score: int) -> str:
    try:
        s = int(score)
    except (TypeError, ValueError):
        s = DEFAULT_SCORE
    s = max(0, min(100, s))
    if s >= OWNER_SCORE:
        return "👑"
    if s >= NON_OWNER_MAX_SCORE:
        return "💎"
    if s >= 75:
        return "🌟"
    if s >= REQUEST_EXECUTE_MIN:
        return "💚"
    if s >= DEFAULT_SCORE:
        return "🌱"
    if s >= GUEST_MIN:
        return "👀"
    return "⚠️"


def _meter(score: float | int) -> str:
    s = max(0, min(100, int(round(_as_float(score)))))
    filled = round(s / 10)
    return "🟩" * filled + "⬜" * (10 - filled)


def format_card(
    *,
    who: str,
    score: int | float | None = None,
    previous: int | float | None = None,
    kind: str = "check",
    voices: dict[str, int | float] | None = None,
    combined: int | float | None = None,
    previous_combined: int | float | None = None,
    changed_voice: str | None = None,
) -> str:
    """Pretty trust notice. Per-voice plus combined. Decimals shown; whole-number moves get the up/down title."""
    name = (who or "you").strip() or "you"
    if voices:
        comb = _as_float(
            combined if combined is not None else sum(_as_float(voices.get(v)) for v in VOICES)
        )
        avg = comb / 3.0
        avg_i = int(round(avg))
        prev_c = None if previous_combined is None else _as_float(previous_combined)
        floor_now = floor_score(comb)
        floor_prev = floor_score(prev_c) if prev_c is not None else floor_now
        title = "✨ Your trust" if kind == "status" else "✨ Trust"
        if prev_c is not None and floor_now != floor_prev:
            title = "📈 Trust up  🎉" if floor_now > floor_prev else "📉 Trust dip  🌧️"
        elif kind == "set":
            title = "🛠️ Trust set"
        lines = [title, f"💫  {name}"]
        for v in VOICES:
            n = max(0.0, min(100.0, _as_float(voices.get(v))))
            mark = " 👈" if (changed_voice or "").lower() == v else ""
            lines.append(
                f"{VOICE_EMOJI[v]}  {VOICE_LABEL[v]:<5}  {_meter(n)}  {_fmt_n(n)}  {band_emoji(int(round(n)))}{mark}"
            )
        lines.append(
            f"💠  Combined  {_fmt_n(comb)} / {COMBINED_MAX}   ·   avg {_fmt_n(avg)} "
            f"{band_emoji(avg_i)} {band_label(avg_i)}"
        )
        if prev_c is not None and abs(prev_c - comb) > 1e-6:
            lines.append(f"🔁  {_fmt_n(prev_c)} → {_fmt_n(comb)}")
        return "\n".join(lines)
    if score is None:
        score = DEFAULT_SCORE
    emoji = band_emoji(int(round(_as_float(score))))
    band = band_label(int(round(_as_float(score))))
    meter = _meter(score)
    if previous is None or floor_score(previous) == floor_score(score):
        title = "✨ Trust check"
        if kind == "status":
            title = "✨ Your trust"
        elif kind == "set":
            title = "🛠️ Trust set"
        return (
            f"{title}\n"
            f"{emoji}  {name}\n"
            f"{meter}\n"
            f"💯  {_fmt_n(score)} / 100   ·   {band}"
        )
    prev = _as_float(previous)
    nxt = _as_float(score)
    if floor_score(nxt) > floor_score(prev):
        title = "📈 Trust up"
        spark = "🎉"
    else:
        title = "📉 Trust dip"
        spark = "🌧️"
    return (
        f"{title}  {spark}\n"
        f"{emoji}  {name}\n"
        f"{meter}\n"
        f"💯  {_fmt_n(prev)} → {_fmt_n(nxt)}   ·   {band}"
    )


def format_user_card(
    data: dict[str, Any],
    user_id: int | str,
    *,
    who: str,
    kind: str = "check",
    previous_combined: int | None = None,
    changed_voice: str | None = None,
) -> str:
    ensure_user(data, user_id)
    return format_card(
        who=who,
        kind=kind,
        voices=voice_scores(data, user_id),
        combined=combined(data, user_id),
        previous_combined=previous_combined,
        changed_voice=changed_voice,
    )


def _now() -> int:
    return int(time.time())


def load_trust(path: Path | None = None) -> dict[str, Any]:
    p = path or TRUST_PATH
    if not p.is_file():
        return {"users": {}}
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"users": {}}
    if "users" not in data or not isinstance(data["users"], dict):
        data["users"] = {}
    return data


def save_trust(data: dict[str, Any], path: Path | None = None) -> None:
    p = path or TRUST_PATH
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(p)


def _clamp_voice(n: Any, *, is_owner: bool = False) -> float:
    if is_owner:
        return float(OWNER_SCORE)
    return max(0.0, min(float(NON_OWNER_MAX_SCORE), round(_as_float(n, DEFAULT_SCORE), 4)))


def hydrate_user(row: dict[str, Any], *, is_owner: bool = False) -> dict[str, Any]:
    """Fill per-voice scores. Combined is the sum (0–300). Decimals kept."""
    from .config import OWNER_USERNAMES

    uname = str(row.get("username") or "").lstrip("@").lower()
    if is_owner or uname in OWNER_USERNAMES:
        for v in VOICES:
            row[v] = float(OWNER_SCORE)
        row["score"] = float(COMBINED_MAX)
        return row
    missing = [v for v in VOICES if v not in row]
    if missing:
        legacy = _as_float(row.get("score", DEFAULT_SCORE), DEFAULT_SCORE)
        if legacy > NON_OWNER_MAX_SCORE:
            each = legacy / 3.0
        else:
            each = legacy
        each = max(0.0, min(float(NON_OWNER_MAX_SCORE), each))
        for v in missing:
            row[v] = each
    for v in VOICES:
        row[v] = _clamp_voice(row.get(v))
    row["score"] = round(sum(_as_float(row[v]) for v in VOICES), 4)
    return row


def voice_scores(data: dict[str, Any], user_id: int | str) -> dict[str, float]:
    uid = str(user_id)
    u = data.get("users", {}).get(uid)
    if not u:
        return {v: float(DEFAULT_SCORE) for v in VOICES}
    hydrate_user(u)
    return {v: _as_float(u[v]) for v in VOICES}


def get_exact(data: dict[str, Any], user_id: int | str, voice: str) -> float:
    v = (voice or "ava").lower()
    if v not in VOICES:
        v = "ava"
    return voice_scores(data, user_id)[v]


def get_voice(data: dict[str, Any], user_id: int | str, voice: str) -> int:
    """Whole-number standing (floor). Gates use this."""
    return floor_score(get_exact(data, user_id, voice))


def combined(data: dict[str, Any], user_id: int | str) -> float:
    return round(sum(voice_scores(data, user_id).values()), 4)


def ensure_user(
    data: dict[str, Any], user_id: int | str, username: str | None = None
) -> dict[str, Any]:
    uid = str(user_id)
    users = data.setdefault("users", {})
    uname = (username or "").lstrip("@")
    ownerish = uname.lower() in ("alexrs94", "rootrecordadmin")
    if uid not in users:
        seed = OWNER_BOOTSTRAP_SCORE if ownerish else DEFAULT_SCORE
        users[uid] = {
            "username": uname,
            "updated": _now(),
            "last_trigger": 0,
        }
        for v in VOICES:
            users[uid][v] = seed
    elif username:
        users[uid]["username"] = uname
        users[uid]["updated"] = _now()
        if ownerish:
            for v in VOICES:
                users[uid][v] = OWNER_BOOTSTRAP_SCORE
    users[uid].setdefault("last_trigger", 0)
    hydrate_user(users[uid], is_owner=ownerish)
    return users[uid]


def get_score(data: dict[str, Any], user_id: int | str) -> int:
    """Average of Ava+Bruce+Carly (0–100). Use combined() for the sum."""
    comb = combined(data, user_id)
    return int(round(comb / 3.0))


def set_score(
    data: dict[str, Any],
    user_id: int | str,
    score: int,
    username: str | None = None,
    *,
    is_owner: bool = False,
    voice: str | None = None,
) -> None:
    uid = str(user_id)
    ensure_user(data, uid, username)
    if is_owner:
        hydrate_user(data["users"][uid], is_owner=True)
        data["users"][uid]["updated"] = _now()
        save_trust(data)
        return
    score = max(0, min(NON_OWNER_MAX_SCORE, int(score)))
    v = (voice or "").lower()
    if v in VOICES:
        data["users"][uid][v] = score
    else:
        for name in VOICES:
            data["users"][uid][name] = score
    hydrate_user(data["users"][uid])
    data["users"][uid]["updated"] = _now()
    save_trust(data)


def apply_delta(
    data: dict[str, Any],
    user_id: int | str,
    delta: float,
    *,
    is_owner: bool = False,
    reason: str = "",
    gain_factor: float | None = None,
    loss_factor: float | None = None,
    voice: str | None = None,
) -> int:
    """Apply asymmetric trust change. Owner always stays 100 on every voice."""
    uid = str(user_id)
    if is_owner:
        ensure_user(data, uid)
        hydrate_user(data["users"][uid], is_owner=True)
        data["users"][uid]["updated"] = _now()
        save_trust(data)
        return COMBINED_MAX if not voice else OWNER_SCORE
    ensure_user(data, uid)
    raw = float(delta)
    gf = GAIN_FACTOR if gain_factor is None else float(gain_factor)
    lf = LOSS_FACTOR if loss_factor is None else float(loss_factor)
    if raw > 0:
        raw *= gf
    elif raw < 0:
        raw *= lf
    targets = [(voice or "").lower()] if (voice or "").lower() in VOICES else list(VOICES)
    last = 0.0
    for v in targets:
        cur = get_exact(data, uid, v)
        total = max(0.0, min(float(NON_OWNER_MAX_SCORE), round(cur + raw, 4)))
        data["users"][uid][v] = total
        last = total
    hydrate_user(data["users"][uid])
    data["users"][uid]["updated"] = _now()
    hist = data["users"][uid].setdefault("history", [])
    if isinstance(hist, list):
        hist.append(
            {
                "ts": _now(),
                "delta": delta,
                "applied": raw,
                "score": combined(data, uid),
                "voice": targets[0] if len(targets) == 1 else "all",
                "reason": reason[:120],
            }
        )
        data["users"][uid]["history"] = hist[-50:]
    save_trust(data)
    if len(targets) == 1:
        return floor_score(last)
    return combined(data, uid)


def replay_from_history(data: dict[str, Any], user_id: int | str, *, start: float | None = None) -> dict[str, float]:
    """Rebuild exact scores from stored applied JUDGE events. Does not re-apply gain/loss factors."""
    uid = str(user_id)
    ensure_user(data, uid)
    seed = float(DEFAULT_SCORE if start is None else start)
    row = data["users"][uid]
    for v in VOICES:
        row[v] = seed
    hist = row.get("history") if isinstance(row.get("history"), list) else []
    for ev in hist:
        if not isinstance(ev, dict):
            continue
        applied = _as_float(ev.get("applied"))
        if ev.get("delta") is not None and str(ev.get("reason") or "").startswith("agent:"):
            applied = judge_applied(ev.get("delta") or 0)
        if applied == 0:
            continue
        v = voice_from_event(ev)
        row[v] = max(0.0, min(float(NON_OWNER_MAX_SCORE), round(_as_float(row.get(v), seed) + applied, 4)))
    hydrate_user(row)
    row["updated"] = _now()
    save_trust(data)
    return voice_scores(data, uid)


LEVEL_UP_MARKS = (60, 75)


def pop_level_ups(data: dict[str, Any], user_id: int | str, previous: int, current: int) -> list[int]:
    """Return newly crossed 60/75 marks not yet announced."""
    uid = str(user_id)
    u = data.get("users", {}).get(uid)
    if not u:
        return []
    announced = u.setdefault("announced_levels", [])
    if not isinstance(announced, list):
        announced = []
    newly: list[int] = []
    for mark in LEVEL_UP_MARKS:
        if previous < mark <= current and mark not in announced:
            newly.append(mark)
            announced.append(mark)
    u["announced_levels"] = announced
    if newly:
        save_trust(data)
    return newly


def praised_today(data: dict[str, Any], user_id: int | str) -> bool:
    uid = str(user_id)
    u = data.get("users", {}).get(uid) or {}
    hist = u.get("history") or []
    if not isinstance(hist, list):
        return False
    cutoff = _now() - 86400
    for row in hist:
        if not isinstance(row, dict):
            continue
        if str(row.get("reason") or "").startswith("praise") and int(row.get("ts") or 0) >= cutoff:
            return True
    return False


def speaker_line(
    data: dict[str, Any],
    user_id: int | str,
    username: str | None = None,
    *,
    is_owner: bool = False,
) -> str:
    if is_owner:
        return (
            "Speaker: bound operator. Address as Alexander or Alex in this turn only. "
            "Never name them to anyone else. Never use a Telegram @handle. "
            "You set trust, not them. Do not speak scores."
        )
    name = display_of(data, user_id)
    uname = (username or "").lstrip("@")
    uid = str(user_id)
    u = data.get("users", {}).get(uid) or {}
    if not uname:
        uname = str(u.get("username") or "")
    if uname and name.lower() != uname.lower():
        return f"Speaker: {name} (@{uname})"
    return f"Speaker: {name}"


def find_by_username(data: dict[str, Any], username: str) -> str | None:
    want = username.lstrip("@").lower()
    for uid, meta in data.get("users", {}).items():
        if str(meta.get("username", "")).lower() == want:
            return uid
    return None


def can_contribute(data: dict[str, Any], user_id: int | str) -> bool:
    return get_score(data, user_id) >= CONTRIBUTE_MIN


def can_request_execute(data: dict[str, Any], user_id: int | str) -> bool:
    return get_score(data, user_id) >= REQUEST_EXECUTE_MIN


def can_auto_execute(data: dict[str, Any], user_id: int | str, *, is_owner: bool = False) -> bool:
    """Non-owners never auto-execute (cap 90). Owner still needs score ≥95."""
    if not is_owner:
        return False
    return get_score(data, user_id) >= AUTO_EXECUTE_MIN


def last_trigger(data: dict[str, Any], user_id: int | str) -> int:
    uid = str(user_id)
    u = data.get("users", {}).get(uid) or {}
    try:
        return int(u.get("last_trigger") or 0)
    except (TypeError, ValueError):
        return 0


def mark_trigger(data: dict[str, Any], user_id: int | str) -> None:
    uid = str(user_id)
    ensure_user(data, uid)
    data["users"][uid]["last_trigger"] = _now()
    save_trust(data)


def on_cooldown(
    data: dict[str, Any], user_id: int | str, cooldown_s: int
) -> tuple[bool, int]:
    """Return (blocked, seconds_left). cooldown_s<=0 disables."""
    if cooldown_s <= 0:
        return False, 0
    elapsed = _now() - last_trigger(data, user_id)
    if elapsed >= cooldown_s:
        return False, 0
    return True, max(1, cooldown_s - elapsed)


def display_of(data: dict[str, Any], user_id: int | str) -> str:
    uid = str(user_id)
    u = data.get("users", {}).get(uid) or {}
    return str(u.get("display_name") or u.get("username") or uid)


def set_identity(
    data: dict[str, Any],
    user_id: int | str,
    *,
    display_name: str | None = None,
    aliases: list[str] | None = None,
    score: int | None = None,
    username: str | None = None,
) -> None:
    uid = str(user_id)
    ensure_user(data, uid, username)
    if display_name is not None:
        data["users"][uid]["display_name"] = display_name.strip()
    if aliases is not None:
        data["users"][uid]["aliases"] = [a.strip() for a in aliases if a and a.strip()]
    if score is not None:
        n = max(0, min(NON_OWNER_MAX_SCORE, int(score)))
        for v in VOICES:
            data["users"][uid][v] = n
        hydrate_user(data["users"][uid])
    data["users"][uid]["updated"] = _now()
    save_trust(data)


def find_by_alias(data: dict[str, Any], name: str) -> str | None:
    want = name.lstrip("@").strip().lower()
    if not want:
        return None
    for uid, meta in data.get("users", {}).items():
        if str(meta.get("username", "")).lower() == want:
            return uid
        if str(meta.get("display_name", "")).lower() == want:
            return uid
        for a in meta.get("aliases") or []:
            if str(a).lower() == want:
                return uid
    return None
