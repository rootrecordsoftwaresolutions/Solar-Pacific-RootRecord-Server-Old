"""Hourly spoken desks from live facts only. Kokoro WAV. No clip stitch.

Ava Heart — weather. Bruce Echo — solar + host. Carly Nova — Kīlauea, security, bandwidth.
Skip a desk when that feed is down or has no measured numbers.
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from apps.core import config
from apps.voice.local_tts import GENERATED
from apps.voice.speakers import is_live, publish_current, speak_report

log = logging.getLogger("ava.cron.hourly_clip_reports")
HST = ZoneInfo("Pacific/Honolulu")
STATE_PATH = config.STATE_DIR / "hourly-clip-reports.json"

CURRENT_NAMES = {
    "solar": "solar-weather-current.wav",
    "system": "system-performance-current.wav",
    "weather": "nws-hawaii-current.wav",
    "kilauea": "Kilauea_Current.wav",
    "security": "security-hourly-current.wav",
    "bandwidth": "bandwidth-hourly-current.wav",
}


def _load_state() -> dict:
    if not STATE_PATH.is_file():
        return {"hashes": {}, "last_played": {}}
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {"hashes": {}, "last_played": {}}
    except Exception:
        return {"hashes": {}, "last_played": {}}


def _save_state(data: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _body_hash(script: str) -> str:
    return hashlib.md5((script or "").encode("utf-8")).hexdigest()


def _clock(now: datetime) -> str:
    from apps.voice.speakable import spoken_clock

    return spoken_clock(now.hour, now.minute) + " Hawaiian Standard Time"


def solar_spoken(facts: str, now: datetime) -> str | None:
    line = ""
    for row in (facts or "").splitlines():
        if row.lower().startswith("ecoflow") or "delta" in row.lower() or "river" in row.lower():
            line = row.strip()
            break
    if not line:
        try:
            from apps.core.services import db_facts

            line = db_facts.ecoflow_line()
        except Exception:
            return None
    text = f"Solar desk at {_clock(now)}. {line}"
    return text if is_live("solar", text) else None


def system_spoken(facts: str, now: datetime) -> str | None:
    line = ""
    for row in (facts or "").splitlines():
        if row.lower().startswith("host"):
            line = row.strip()
            break
    if not line:
        try:
            from apps.core.services import db_facts

            line = db_facts.host_line()
        except Exception:
            return None
    text = f"Host desk at {_clock(now)}. {line}"
    try:
        from apps.core.host_metrics import host_drives_spoken

        drives = host_drives_spoken()
        if drives:
            text = f"{text} {drives}"
    except Exception:
        pass
    return text if is_live("system", text) else None


def weather_spoken(facts: str, now: datetime) -> str | None:
    lines = []
    for row in (facts or "").splitlines():
        low = row.lower()
        if low.startswith("weather") or low.startswith("hi alerts"):
            lines.append(row.strip())
    if not lines:
        try:
            from apps.core.services import live_wx

            lines = [x for x in (live_wx.weather_lines_sync() or []) if x]
        except Exception:
            lines = []
    blob = " ".join(lines)
    if not blob or "weather: down" in blob.lower():
        return None
    text = f"Weather desk at {_clock(now)}. {blob}"
    return text if is_live("weather", text) else None


def kilauea_spoken(facts: str, now: datetime) -> str | None:
    line = ""
    for row in (facts or "").splitlines():
        if "kilauea" in row.lower():
            line = row.strip()
            break
    if not line:
        try:
            from apps.core.services.persona import _kilauea_line

            line = _kilauea_line()
        except Exception:
            return None
    text = f"Kilauea desk at {_clock(now)}. {line}"
    return text if is_live("kilauea", text) else None


def security_spoken(facts: str, now: datetime) -> str | None:
    try:
        from apps.core.host_metrics import security_spoken as live
    except Exception:
        return None
    text = live(now)
    return text if text and is_live("security", text) else None


def bandwidth_spoken(facts: str, now: datetime) -> str | None:
    try:
        from apps.core.host_metrics import bandwidth_spoken as live
    except Exception:
        return None
    text = live(now)
    return text if text and is_live("bandwidth", text) else None


def _write_desk_md(cat: str, spoken: str, now: datetime) -> None:
    if cat not in {"security", "bandwidth"}:
        return
    try:
        config.REPORTS_DIR.mkdir(parents=True, exist_ok=True)
        body = f"# {cat.title()} — {now.strftime('%Y-%m-%d %H:%M')} Hawaiian Standard Time\n\n{spoken.strip()}\n"
        dated = config.REPORTS_DIR / f"{cat}-hourly-{now.strftime('%Y-%m-%dT%H')}.md"
        current = config.REPORTS_DIR / f"{cat}-hourly-current.md"
        dated.write_text(body, encoding="utf-8")
        current.write_text(body, encoding="utf-8")
    except OSError as e:
        log.debug("hourly %s markdown skipped: %s", cat, e)


def _facts_sync() -> str:
    from apps.core.services import db_facts
    from apps.core.services.persona import _kilauea_line

    lines = [db_facts.ecoflow_line(), db_facts.host_line(), _kilauea_line()]
    try:
        from apps.core.services import live_wx

        lines.extend(live_wx.weather_lines_sync())
    except Exception:
        pass
    return "\n".join(lines)


async def _facts_live() -> str:
    try:
        from apps.core.services import persona as persona_svc

        return await persona_svc.live_facts()
    except Exception:
        return _facts_sync()


def build_all(facts: str | None = None, *, force: bool = False) -> dict[str, dict]:
    facts = _facts_sync() if facts is None else facts
    now = datetime.now(HST)
    stamp = now.strftime("%Y%m%d-%H")
    st = _load_state()
    hashes = dict(st.get("hashes") or {})
    jobs = {
        "solar": solar_spoken(facts, now),
        "system": system_spoken(facts, now),
        "weather": weather_spoken(facts, now),
        "kilauea": kilauea_spoken(facts, now),
        "security": security_spoken(facts, now),
        "bandwidth": bandwidth_spoken(facts, now),
    }
    kinds = {
        "solar": "solar",
        "system": "system",
        "weather": "weather",
        "kilauea": "kilauea",
        "security": "security",
        "bandwidth": "bandwidth",
    }
    out: dict[str, dict] = {}
    for cat, spoken in jobs.items():
        latest = Path(GENERATED) / f"hourly-{cat}-current.wav"
        if not spoken:
            out[cat] = {"ok": True, "skipped": True, "detail": "no_live_data", "changed": False}
            log.info("hourly %s skip — no live data", cat)
            continue
        fp = _body_hash(spoken)
        changed = force or fp != str(hashes.get(cat) or "")
        result: dict = {
            "ok": True,
            "changed": changed,
            "hash": fp,
            "script": spoken,
            "skipped_rebuild": not changed,
        }
        if changed:
            dest = Path(GENERATED) / f"hourly-{cat}-{stamp}.wav"
            result = speak_report(kinds[cat], spoken, dest)
            result["script"] = spoken
            result["changed"] = bool(result.get("ok"))
            result["hash"] = fp
            if result.get("ok"):
                extras = [latest]
                current_name = CURRENT_NAMES.get(cat)
                if current_name:
                    extras.append(Path(config.AUDIO_CURRENT_DIR) / current_name)
                publish_current(dest, *extras)
                hashes[cat] = fp
                _write_desk_md(cat, spoken, now)
                try:
                    from apps.council.report_cast import notify_report

                    notify_report(
                        cat if cat != "weather" else "nws",
                        transcript=spoken,
                        audio=latest if latest.is_file() else dest,
                    )
                except Exception as e:
                    log.debug("hourly %s telegram skipped: %s", cat, e)
        out[cat] = result
        log.info("hourly %s changed=%s ok=%s", cat, result.get("changed"), result.get("ok"))
    st["hashes"] = hashes
    st["updated_at"] = now.isoformat()
    _save_state(st)
    Path(GENERATED).mkdir(parents=True, exist_ok=True)
    (Path(GENERATED) / "hourly-scripts.txt").write_text(
        "\n".join(
            f"{k} READ: {v.get('read') or v.get('script') or v.get('detail')}\n"
            f"{k} SPEAK: {v.get('speak') or v.get('script') or v.get('detail')}"
            for k, v in out.items()
        )
        + "\n",
        encoding="utf-8",
    )
    return out


async def prebuild() -> dict:
    facts = await _facts_live()
    return await asyncio.to_thread(build_all, facts)


async def play(*, only_changed: bool = True) -> dict:
    from apps.voice.director import Priority, get_director

    director = get_director()
    st = _load_state()
    played = []
    for cat in ("solar", "system", "weather", "kilauea", "security", "bandwidth"):
        wav = Path(GENERATED) / f"hourly-{cat}-current.wav"
        if not wav.is_file():
            continue
        await director.queue(wav, name=f"hourly_{cat}", priority=Priority.REPORT, scene=None)
        played.append(cat)
    st["last_played"] = {c: datetime.now(HST).isoformat() for c in played}
    _save_state(st)
    return {"ok": True, "played": played, "skipped": []}


async def run() -> dict:
    built = await asyncio.to_thread(build_all, await _facts_live())
    from apps.voice.director import Priority, get_director

    director = get_director()
    played = []
    for cat, row in built.items():
        if not row.get("changed"):
            continue
        wav = Path(GENERATED) / f"hourly-{cat}-current.wav"
        if not wav.is_file():
            continue
        await director.queue(wav, name=f"hourly_{cat}", priority=Priority.REPORT, scene=None)
        played.append(cat)
    return {"built": {k: v.get("ok") for k, v in built.items()}, "played": played, "changed": played}
