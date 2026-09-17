"""Lift a liked DM idea into the group as a proposal. Never dump the private thread."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any

from . import chatlog, proposals, sanitize, telegram, trust
from .config import CONFIG_DIR, Config

PATH = CONFIG_DIR / "dm-lifts.json"
PROPOSE_RE = re.compile(r"<<<PROPOSE\s+([^>]*?)>>>", re.I)
SHARE_ASK = re.compile(
    r"(?i)\b(?:"
    r"suggest (?:that|this|it) to (?:your |the )?(?:peers|group|team|others|room)|"
    r"tell (?:the )?(?:group|team|others|peers)|"
    r"post (?:that|this|it) (?:to|in) (?:the )?(?:group|chat)|"
    r"bring (?:that|this|it) to (?:the )?(?:group|team)|"
    r"share (?:that|this|it) with (?:the )?(?:group|team|others)"
    r")\b"
)
DAY_CAP = 3
PITCH_CAP = 700


def _now() -> int:
    return int(time.time())


def looks_share_ask(text: str) -> bool:
    return bool(SHARE_ASK.search(text or ""))


def parse_propose(text: str) -> str:
    m = PROPOSE_RE.search(text or "")
    if not m:
        return ""
    return re.sub(r"\s+", " ", (m.group(1) or "").strip())[:PITCH_CAP]


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


def _day_key(now: int | None = None) -> str:
    return time.strftime("%Y%m%d", time.gmtime(int(now if now is not None else _now())))


def _room(user_id: int | str, now: int | None = None) -> bool:
    data = _load()
    uid = str(user_id)
    key = _day_key(now)
    row = data.get("users", {}).get(uid)
    if not isinstance(row, dict) or str(row.get("day") or "") != key:
        return True
    try:
        n = int(row.get("n") or 0)
    except (TypeError, ValueError):
        n = 0
    return n < DAY_CAP


def _mark(user_id: int | str, now: int | None = None) -> None:
    data = _load()
    uid = str(user_id)
    key = _day_key(now)
    users = data.setdefault("users", {})
    row = users.get(uid)
    if not isinstance(row, dict) or str(row.get("day") or "") != key:
        row = {"day": key, "n": 0}
    row["n"] = int(row.get("n") or 0) + 1
    row["day"] = key
    row["last"] = int(now if now is not None else _now())
    users[uid] = row
    _save(data)


def fallback_pitch(dm_chat_id: int | str, voice: str) -> str:
    rows = chatlog.for_chat(dm_chat_id)
    for r in reversed(rows):
        if r.get("dir") != "out":
            continue
        if str(r.get("voice") or "") != voice:
            continue
        t = re.sub(r"\s+", " ", str(r.get("text") or "").strip())
        if len(t) >= 40:
            return t[:PITCH_CAP]
    return ""


def group_id(st: dict[str, Any], cfg: Config) -> str:
    return str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "").strip()


def maybe_lift(
    cfg: Config,
    st: dict[str, Any],
    *,
    voice: str,
    raw: str,
    meta: dict[str, Any],
    dm_chat_id: int | str,
) -> dict[str, Any]:
    """If this DM turn liked an idea, file it on the group daily plan. No private transcript."""
    if not meta.get("dm"):
        return {"ok": False, "detail": "not_dm"}
    if meta.get("nsfw"):
        return {"ok": False, "detail": "nsfw"}
    voice = voice if voice in ("ava", "bruce", "carly") else "ava"
    uid = meta.get("judge_user_id")
    user_text = str(meta.get("user_text") or "")
    pitch = parse_propose(raw)
    if not pitch and looks_share_ask(user_text):
        pitch = fallback_pitch(dm_chat_id, voice)
    if not pitch:
        return {"ok": False, "detail": "no_pitch"}
    gid = group_id(st, cfg)
    if not gid:
        return {"ok": False, "detail": "no_group"}
    if uid is None:
        return {"ok": False, "detail": "no_user"}
    if not meta.get("judge_is_owner"):
        tdata = trust.load_trust()
        if trust.get_score(tdata, uid) < trust.CONTRIBUTE_MIN:
            return {"ok": False, "detail": "trust"}
        if not _room(uid):
            return {"ok": False, "detail": "rate"}
    pitch = sanitize.sanitize_outbound(pitch, voice=voice, allow_operator_name=False)
    if pitch == sanitize.FALLBACK or len(pitch) < 24:
        return {"ok": False, "detail": "thin"}
    from . import people

    row = people.dossier(uid)
    feats = row.get("features") if isinstance(row.get("features"), dict) else {}
    if meta.get("judge_is_owner") or str(feats.get("relationship") or "") == "owner":
        who = "a private note"
    else:
        who = str(row.get("display_name") or row.get("username") or "a DM").strip() or "a DM"
    origin = f"DM idea ({who}, liked by {voice}): {pitch}"
    rec = proposals.publish(
        chat_id=gid,
        origin=origin,
        thread_id=f"dm-lift-{uid}",
        notes="Lifted from a DM. Idea only. Private thread was not copied.",
    )
    path = rec.get("path") if isinstance(rec.get("path"), str) else str(rec.get("path") or "")
    pid = rec.get("id") or ""
    intro = (
        f"I liked this idea from a DM enough to bring it here.\n\n{pitch}\n\n"
        f"Filing it on the daily plan."
    )
    telegram.send_message(
        cfg.token_for(voice),
        gid,
        sanitize.sanitize_outbound(intro, voice=voice, allow_operator_name=False)[:3500],
    )
    if path:
        cap = (
            f"Daily proposal {pid} — DM idea, no private transcript.\n"
            f"Owner: /done when finished, /approve {pid}"
        )
        res = telegram.send_document(
            cfg.token_for("bruce"),
            gid,
            Path(path),
            caption=sanitize.sanitize_outbound(cap, voice="bruce")[:900],
        )
        mid = ((res.get("result") or {}) if isinstance(res.get("result"), dict) else {}).get(
            "message_id"
        )
        if mid:
            proposals.remember_telegram_message(pid, mid)
    if not meta.get("judge_is_owner"):
        _mark(uid)
    telegram.send_message(
        cfg.token_for(voice),
        dm_chat_id,
        sanitize.sanitize_outbound(
            "Took that to the group as a proposal.",
            voice=voice,
        ),
    )
    print(f"dm-lift voice={voice} plan={pid} user={uid}", flush=True)
    return {"ok": True, "id": pid, "pitch": pitch}
