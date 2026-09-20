"""Agents may nudge human trust. Owner is frozen. Tags never go to Telegram."""
from __future__ import annotations

import re
import time
from typing import Any

from . import state, trust
from .config import Config

JUDGE_RE = re.compile(
    r"<<<JUDGE(?:\s+delta\s*=\s*|\s+)([+-]?\d+)\s+(?:reason\s*=\s*)?([^>]*?)>>>",
    re.I,
)
DELTA_MIN = -3
DELTA_MAX = 3
HOUR_POS_CAP = 6
HOUR_NEG_CAP = -12


def parse(text: str) -> dict[str, Any] | None:
    m = JUDGE_RE.search(text or "")
    if not m:
        return None
    try:
        delta = int(m.group(1))
    except (TypeError, ValueError):
        return None
    reason = (m.group(2) or "").strip()[:80]
    return {"delta": delta, "reason": reason}


def strip(text: str) -> str:
    return JUDGE_RE.sub("", text or "").strip()


def _hour_key(now: int | None = None) -> str:
    now = int(now if now is not None else time.time())
    return time.strftime("%Y%m%d%H", time.gmtime(now))


def _budget(user: dict[str, Any], voice: str, now: int | None = None) -> tuple[int, int]:
    blob = user.get("agent_judge") if isinstance(user.get("agent_judge"), dict) else {}
    key = _hour_key(now)
    if "pos" in blob and "ava" not in blob:
        row = blob if (voice or "ava") == "ava" else {}
    else:
        row = blob.get(voice) if isinstance(blob.get(voice), dict) else {}
    if str(row.get("hour") or "") != key:
        return 0, 0
    try:
        pos = int(row.get("pos") or 0)
    except (TypeError, ValueError):
        pos = 0
    try:
        neg = int(row.get("neg") or 0)
    except (TypeError, ValueError):
        neg = 0
    return pos, neg


def clamp_delta(delta: int, pos_used: int, neg_used: int) -> int:
    d = max(DELTA_MIN, min(DELTA_MAX, int(delta)))
    if d > 0:
        room = HOUR_POS_CAP - pos_used
        return max(0, min(d, room))
    if d < 0:
        room = HOUR_NEG_CAP - neg_used  # e.g. -12 - (-4) = -8 remaining toward more neg
        # remaining capacity for additional negative raw
        remain = HOUR_NEG_CAP - neg_used
        if remain >= 0:
            return 0
        return max(d, remain)
    return 0


def apply_agent_delta(
    trust_data: dict[str, Any],
    user_id: int | str,
    delta: int,
    *,
    voice: str,
    reason: str,
    is_owner: bool,
    gain_factor: float | None = None,
    loss_factor: float | None = None,
) -> dict[str, Any]:
    uid = str(user_id)
    if is_owner:
        return {"ok": False, "detail": "owner_frozen", "delta": 0}
    trust.ensure_user(trust_data, uid)
    user = trust_data["users"][uid]
    pos_used, neg_used = _budget(user, voice)
    d = clamp_delta(delta, pos_used, neg_used)
    if d == 0:
        return {"ok": True, "skipped": True, "delta": 0, "score": trust.get_voice(trust_data, uid, voice)}
    prev_v = trust.get_voice(trust_data, uid, voice)
    prev_c = trust.combined(trust_data, uid)
    key = _hour_key()
    blob = user.get("agent_judge") if isinstance(user.get("agent_judge"), dict) else {}
    if "pos" in blob and "ava" not in blob:
        blob = {"ava": {"hour": blob.get("hour"), "pos": blob.get("pos"), "neg": blob.get("neg")}}
    sub = blob.get(voice) if isinstance(blob.get(voice), dict) else {}
    if str(sub.get("hour") or "") != key:
        sub = {"hour": key, "pos": 0, "neg": 0}
    if d > 0:
        sub["pos"] = int(sub.get("pos") or 0) + d
    else:
        sub["neg"] = int(sub.get("neg") or 0) + d
    sub["hour"] = key
    blob[voice] = sub
    user["agent_judge"] = blob
    nxt_v = trust.apply_delta(
        trust_data,
        uid,
        trust.judge_applied(d),
        is_owner=False,
        reason=f"agent:{voice}:{reason}"[:120],
        gain_factor=1.0,
        loss_factor=1.0,
        voice=voice,
    )
    return {
        "ok": True,
        "skipped": False,
        "delta": d,
        "previous": prev_v,
        "score": nxt_v,
        "previous_combined": prev_c,
        "combined": trust.combined(trust_data, uid),
        "voice": voice,
        "reason": reason,
    }


def maybe_announce(
    cfg: Config,
    chat_id: int | str,
    trust_data: dict[str, Any],
    user_id: int | str,
    result: dict[str, Any],
) -> None:
    if not result.get("ok") or result.get("skipped"):
        return
    prev = int(result.get("previous") or 0)
    nxt = int(result.get("score") or prev)
    prev_c = trust._as_float(result.get("previous_combined"))
    nxt_c = trust._as_float(result.get("combined"), prev_c)
    if prev == nxt and trust.floor_score(prev_c) == trust.floor_score(nxt_c):
        return
    who = trust.display_of(trust_data, user_id)
    voice = str(result.get("voice") or "ava")
    note = str(result.get("reason") or "").strip()
    extra = f" ({note})" if note else ""
    print(
        f"trust-judge {who} {voice} {prev}->{nxt} combined {prev_c}->{nxt_c}{extra} chat={chat_id}",
        flush=True,
    )
    try:
        cid = int(chat_id)
    except (TypeError, ValueError):
        return
    if cid < 0:
        return
    from . import telegram

    body = trust.format_card(
        who=who,
        kind="set",
        voices=trust.voice_scores(trust_data, user_id),
        combined=nxt_c,
        previous_combined=prev_c,
        changed_voice=voice,
    )
    telegram.send_message(cfg.token_for("ava"), chat_id, body)


def apply_from_reply(
    cfg: Config,
    st: dict[str, Any],
    raw: str,
    *,
    voice: str,
    meta: dict[str, Any],
    chat_id: int | str,
) -> dict[str, Any]:
    parsed = parse(raw)
    if not parsed:
        return {"ok": True, "skipped": True, "detail": "no_tag"}
    uid = meta.get("judge_user_id")
    if uid is None:
        return {"ok": False, "detail": "no_target"}
    owner = bool(meta.get("judge_is_owner")) or str(uid) == str(state.owner_id(st, cfg))
    from . import trust as trust_mod

    data = trust_mod.load_trust(cfg.trust_path)
    out = apply_agent_delta(
        data,
        uid,
        int(parsed["delta"]),
        voice=voice,
        reason=str(parsed.get("reason") or ""),
        is_owner=owner,
        gain_factor=cfg.trust_gain_factor,
        loss_factor=cfg.trust_loss_factor,
    )
    maybe_announce(cfg, chat_id, data, uid, out)
    return out
