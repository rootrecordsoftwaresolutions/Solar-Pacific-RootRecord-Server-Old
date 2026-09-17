"""Replay morning-boot MP3 every :30 HST until noon (same day). No TTS spend."""

from __future__ import annotations

import json
import logging
import re
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

log = logging.getLogger("ava.cron.morning_boot_replay")
HST = ZoneInfo("Pacific/Honolulu")
STATE_NAME = "morning-boot-replay.json"


def _state_path() -> Path:
    from apps.core import config

    return config.STATE_DIR / STATE_NAME


def _load() -> dict:
    p = _state_path()
    if not p.is_file():
        return {}
    try:
        # utf-8-sig: PowerShell Set-Content -Encoding utf8 may write a BOM that
        # plain utf-8 json.loads rejects → empty state → silent skip (no play).
        data = json.loads(p.read_text(encoding="utf-8-sig"))
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def _save(data: dict) -> None:
    p = _state_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def disarm(*, reason: str = "operator") -> dict:
    """Stop further morning-boot MP3 replays for the armed day."""
    now = datetime.now(HST)
    st = _load()
    if not st:
        return {"ok": True, "skipped": True, "reason": "no_state"}
    was = bool(st.get("enabled"))
    st["enabled"] = False
    st["play_once"] = False
    st["stopped_at"] = now.isoformat()
    st["stop_reason"] = str(reason or "operator")[:160]
    _save(st)
    log.info("morning-boot replay disarmed (%s) was_enabled=%s", reason, was)
    return {"ok": True, "disarmed": True, "was_enabled": was, "reason": st["stop_reason"]}


async def run() -> dict:
    st = _load()
    if not st.get("enabled"):
        return {"ok": True, "skipped": True, "reason": "disabled"}

    now = datetime.now(HST)
    until_raw = str(st.get("until") or "").strip()
    try:
        until = datetime.fromisoformat(until_raw)
        if until.tzinfo is None:
            until = until.replace(tzinfo=HST)
    except ValueError:
        until = now.replace(hour=12, minute=0, second=0, microsecond=0)

    # Hard ceiling: never honor until past noon of that calendar day.
    until_noon = until.replace(hour=12, minute=0, second=0, microsecond=0)
    if until > until_noon:
        until = until_noon

    if now >= until:
        return disarm(reason="past_until")

    today = now.strftime("%Y-%m-%d")
    armed_day = str(st.get("day") or st.get("armed_day") or "").strip()
    if not armed_day:
        # Infer from until / created_at when older state lacked day.
        for key in ("until", "created_at"):
            raw = str(st.get(key) or "")
            if len(raw) >= 10 and raw[4] == "-" and raw[7] == "-":
                armed_day = raw[:10]
                break
    if armed_day and armed_day != today:
        return disarm(reason="stale_day")

    # Midday already landed today → disarm (even if until was wrong).
    try:
        from apps.core.services import daily_report_board

        board = daily_report_board.ensure_today()
        mid = (board.get("slots") or {}).get("midday") or {}
        if str(mid.get("status") or "") == "done":
            return disarm(reason="midday_ok")
    except Exception as e:
        log.debug("midday gate check skipped: %s", e)

    play_once = bool(st.get("play_once"))
    # Scheduled fires are :32 only (after :30 chime); play_once may run any minute.
    if not play_once and now.minute != 32:
        return {"ok": True, "skipped": True, "reason": "not_:32"}

    mp3 = Path(str(st.get("mp3") or ""))
    if not mp3.is_file():
        current = Path(str(st.get("current") or ""))
        mp3 = current if current.is_file() else mp3
    if not mp3.is_file():
        log.warning("morning-boot replay missing mp3")
        return {"ok": False, "detail": "mp3_missing"}

    # Morning-boot only — refuse midday/evening filenames.
    low = mp3.name.lower()
    if "midday" in low or "evening" in low or "late-report" in low:
        log.warning("morning-boot replay refused non-morning file %s", mp3.name)
        return disarm(reason="wrong_mp3_type")

    # Refuse dated archives that are not today.
    dated = re.search(r"(20\d{2}-\d{2}-\d{2})", mp3.name)
    if dated and dated.group(1) != today:
        log.warning("morning-boot replay refused stale dated file %s", mp3.name)
        return disarm(reason="stale_mp3_day")
    day_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    try:
        if datetime.fromtimestamp(mp3.stat().st_mtime, tz=HST) < day_start:
            log.warning("morning-boot replay refused stale mtime %s", mp3.name)
            return disarm(reason="stale_mp3_mtime")
    except OSError:
        return {"ok": False, "detail": "mp3_stat_failed"}

    from apps.voice.director import Priority, get_director

    await get_director().queue(
        mp3,
        name="morning_boot",
        priority=Priority.REPORT,
        scene=None,
    )
    st["play_once"] = False
    st["last_played_at"] = now.isoformat()
    st["last_played"] = str(mp3)
    _save(st)
    log.info("morning-boot replay queued %s", mp3.name)
    return {"ok": True, "played": True, "mp3": str(mp3), "play_once_cleared": play_once}
