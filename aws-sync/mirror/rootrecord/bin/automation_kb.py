#!/usr/bin/env python3
"""Durable RootRecord automation knowledgebase (etc/ — packer never wipes).

Any AWS poller/automation may read/update sections. Self-updates on each touch.
"""
from __future__ import annotations

import json
import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import httpx

from common import ETC, WORK, now_hst, write_json

HST = ZoneInfo("Pacific/Honolulu")
LAT = float(os.environ.get("RR_SOLAR_LAT") or os.environ.get("RR_SITE_LAT") or 19.43)
LON = float(os.environ.get("RR_SOLAR_LON") or os.environ.get("RR_SITE_LON") or -155.23)
KB_PATH = ETC / "automation-kb.json"
LEGACY_PATH = ETC / "solar-cam-kb.json"
OPEN_METEO = (
    "https://api.open-meteo.com/v1/forecast"
    f"?latitude={LAT}&longitude={LON}"
    "&daily=sunrise,sunset"
    "&timezone=Pacific/Honolulu"
    "&forecast_days=2"
)


def _today() -> str:
    return now_hst().strftime("%Y-%m-%d")


def _hhmm_from_iso(iso: str) -> str:
    raw = str(iso or "").strip()
    if "T" in raw:
        raw = raw.split("T", 1)[1]
    return raw[:5]


def _default() -> dict[str, Any]:
    return {
        "brand": "RootRecord",
        "site": {
            "label": "Fern Forest / Puna",
            "lat": LAT,
            "lon": LON,
            "tz": "Pacific/Honolulu",
        },
        "sun": {},
        "solar_cam": {
            "public_host": "origin.avaivy.cloud",
            "channel": 1,
            "power": "river_car_12v",
            "poll_interval_s": int(float(os.environ.get("RR_SOLAR_INTERVAL_S") or 600)),
            "active_window": "sunrise_to_sunset",
        },
        "weather": {},
        "noaa": {},
        "radar": {},
        "earthquake": {},
        "hurricane": {},
        "packer": {},
        "daylight_now": None,
    }


def read() -> dict[str, Any]:
    path = KB_PATH if KB_PATH.is_file() else LEGACY_PATH
    if not path.is_file():
        return _default()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return _default()
    if not isinstance(raw, dict):
        return _default()
    # migrate legacy flat solar-cam-kb shape
    if path == LEGACY_PATH or ("poll" in raw and "solar_cam" not in raw):
        migrated = _default()
        migrated["site"] = raw.get("site") or migrated["site"]
        migrated["sun"] = raw.get("sun") or {}
        migrated["daylight_now"] = raw.get("daylight_now")
        sc = dict(migrated["solar_cam"])
        sc.update(raw.get("cam") or {})
        if raw.get("poll"):
            sc["poll"] = raw["poll"]
        if raw.get("window"):
            sc["window"] = raw["window"]
        migrated["solar_cam"] = sc
        migrated["updated_at"] = raw.get("updated_at")
        return migrated
    base = _default()
    base.update(raw)
    return base


def write(payload: dict[str, Any]) -> dict[str, Any]:
    ETC.mkdir(parents=True, exist_ok=True)
    payload = dict(payload)
    payload["brand"] = "RootRecord"
    payload["updated_at"] = now_hst().isoformat()
    write_json(KB_PATH, payload)
    # keep legacy pointer file as a tiny redirect for old readers
    write_json(
        LEGACY_PATH,
        {
            "moved_to": str(KB_PATH),
            "note": "Use etc/automation-kb.json — shared durable automation KB",
            "updated_at": payload["updated_at"],
        },
    )
    return payload


def update_section(name: str, patch: dict[str, Any], *, merge: bool = True) -> dict[str, Any]:
    kb = read()
    cur = dict(kb.get(name) or {}) if merge else {}
    if merge:
        cur.update(patch)
        kb[name] = cur
    else:
        kb[name] = dict(patch)
    return write(kb)


def _parse_hhmm(value: str) -> tuple[int, int] | None:
    raw = str(value or "").strip()
    if ":" not in raw:
        return None
    hh, mm = raw.split(":", 1)
    try:
        h, m = int(hh), int(mm[:2])
    except ValueError:
        return None
    if not (0 <= h <= 23 and 0 <= m <= 59):
        return None
    return h, m


def _at_today(hhmm: str, day: datetime | None = None) -> datetime | None:
    parsed = _parse_hhmm(hhmm)
    if not parsed:
        return None
    day = day or now_hst()
    return day.replace(hour=parsed[0], minute=parsed[1], second=0, microsecond=0)


def refresh_sun(*, force: bool = False) -> dict[str, Any]:
    kb = read()
    sun = dict(kb.get("sun") or {})
    if not force and sun.get("date") == _today() and sun.get("sunrise") and sun.get("sunset"):
        return sun
    try:
        with httpx.Client(timeout=15.0) as client:
            r = client.get(OPEN_METEO)
            r.raise_for_status()
            daily = (r.json() or {}).get("daily") or {}
        rises = daily.get("sunrise") or []
        sets = daily.get("sunset") or []
        if not rises or not sets:
            return sun
        sun = {
            "date": _today(),
            "sunrise": _hhmm_from_iso(rises[0]),
            "sunset": _hhmm_from_iso(sets[0]),
            "sunrise_iso": rises[0],
            "sunset_iso": sets[0],
            "source": "open-meteo",
            "lat": LAT,
            "lon": LON,
        }
        if len(rises) > 1:
            sun["next_date"] = (now_hst() + timedelta(days=1)).strftime("%Y-%m-%d")
            sun["next_sunrise"] = _hhmm_from_iso(rises[1])
            sun["next_sunrise_iso"] = rises[1]
        kb["sun"] = sun
        kb.setdefault("site", {"lat": LAT, "lon": LON, "tz": "Pacific/Honolulu", "label": "Fern Forest / Puna"})
        write(kb)
        return sun
    except Exception:
        return sun


def daylight_window(sun: dict[str, Any] | None = None) -> dict[str, Any]:
    sun = sun or refresh_sun()
    now = now_hst()
    rise = _at_today(str(sun.get("sunrise") or ""))
    sett = _at_today(str(sun.get("sunset") or ""))
    if rise is None or sett is None:
        rise = now.replace(hour=6, minute=15, second=0, microsecond=0)
        sett = now.replace(hour=18, minute=30, second=0, microsecond=0)
        fallback = True
    else:
        fallback = False
    before = now < rise
    after = now > sett
    daylight = (not before) and (not after)
    skip = "before_sunrise" if before else ("after_sunset" if after else None)
    return {
        "daylight_now": daylight,
        "before_sunrise": before,
        "after_sunset": after,
        "skip_reason": skip,
        "sunrise": sun.get("sunrise") or "",
        "sunset": sun.get("sunset") or "",
        "now_hst": now.strftime("%H:%M"),
        "fallback_sun": fallback,
        "rise_dt": rise,
        "set_dt": sett,
    }


def seconds_until_sunrise(sun: dict[str, Any] | None = None) -> float:
    sun = sun or refresh_sun()
    now = now_hst()
    win = daylight_window(sun)
    if win["daylight_now"]:
        return 0.0
    rise = win["rise_dt"]
    if win["before_sunrise"] and isinstance(rise, datetime):
        return max(60.0, (rise - now).total_seconds())
    if sun.get("next_sunrise_iso"):
        try:
            raw = str(sun["next_sunrise_iso"])
            day = datetime.fromisoformat(raw.replace("Z", "")).replace(tzinfo=HST)
            return max(60.0, (day - now).total_seconds())
        except Exception:
            pass
    hhmm = str(sun.get("next_sunrise") or sun.get("sunrise") or "06:15")
    tomorrow = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    parsed = _parse_hhmm(hhmm)
    if not parsed:
        return 1800.0
    target = tomorrow.replace(hour=parsed[0], minute=parsed[1])
    return max(60.0, (target - now).total_seconds())


def _bump_day_counters(section: dict[str, Any], *, kind: str) -> None:
    day = section.get("day") or ""
    today = _today()
    if day != today:
        section["day"] = today
        section["ok_today"] = 0
        section["fail_today"] = 0
        section["skip_today"] = 0
    key = {"ok": "ok_today", "fail": "fail_today", "skip": "skip_today"}.get(kind)
    if key:
        section[key] = int(section.get(key) or 0) + 1


def update_solar_cam(result: dict[str, Any], *, skipped: bool = False, skip_reason: str | None = None) -> dict[str, Any]:
    kb = read()
    sun = refresh_sun()
    win = daylight_window(sun)
    kb["sun"] = sun
    kb["daylight_now"] = bool(win["daylight_now"])
    sc = dict(kb.get("solar_cam") or {})
    sc.setdefault("public_host", "origin.avaivy.cloud")
    sc.setdefault("channel", 1)
    sc.setdefault("power", "river_car_12v")
    sc["active_window"] = "sunrise_to_sunset"
    sc["poll_interval_s"] = int(float(os.environ.get("RR_SOLAR_INTERVAL_S") or 600))
    poll = dict(sc.get("poll") or {})
    poll["last_attempt_at"] = now_hst().isoformat()
    poll["last_skip_reason"] = skip_reason
    if skipped:
        _bump_day_counters(poll, kind="skip")
    else:
        ok = bool(result.get("ok"))
        _bump_day_counters(poll, kind="ok" if ok else "fail")
        poll["last_ok"] = ok
        if ok:
            poll["last_ok_at"] = result.get("updated_at") or now_hst().isoformat()
            poll["last_lit"] = result.get("lit")
            poll["last_bytes"] = result.get("bytes")
            poll["last_gif"] = result.get("gif")
            poll["last_frame"] = result.get("frame")
        else:
            poll["last_error"] = str((result.get("detail") or result.get("error") or ""))[:240]
    sc["poll"] = poll
    sc["window"] = {
        "sunrise": win.get("sunrise"),
        "sunset": win.get("sunset"),
        "now_hst": win.get("now_hst"),
        "daylight_now": win.get("daylight_now"),
        "fallback_sun": win.get("fallback_sun"),
    }
    kb["solar_cam"] = sc
    kb["window"] = sc["window"]
    sync_from_work(kb, write_back=False)
    return write(kb)


def _read_meta(rel: str) -> dict[str, Any] | None:
    path = WORK / rel
    if not path.is_file():
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    return raw if isinstance(raw, dict) else None


def sync_from_work(kb: dict[str, Any] | None = None, *, write_back: bool = True) -> dict[str, Any]:
    """Pull lightweight Current.meta snapshots into the durable KB."""
    kb = dict(kb or read())
    mapping = {
        "weather": "weather/Current.meta.json",
        "noaa": "noaa/Current.meta.json",
        "radar": "radar/Current.meta.json",
        "earthquake": "earthquakes/Current.meta.json",
        "hurricane": "hurricane/Current.meta.json",
        "kilauea": "kilauea/Current.meta.json",
    }
    for section, rel in mapping.items():
        meta = _read_meta(rel)
        if not meta:
            continue
        slim = {
            "ok": meta.get("ok"),
            "updated_at": meta.get("updated_at"),
            "current": meta.get("current"),
            "bytes": meta.get("bytes"),
            "sha256_16": meta.get("sha256_16"),
            "source": meta.get("source") or meta.get("url"),
        }
        # keep a few section-specific crumbs
        for k in (
            "feature_count",
            "forecast_periods",
            "alert_feature_count",
            "count",
            "events",
            "storms",
        ):
            if k in meta:
                slim[k] = meta[k]
        cur = dict(kb.get(section) or {})
        cur.update({k: v for k, v in slim.items() if v is not None})
        kb[section] = cur

    # first forecast period crumb for automations
    try:
        noaa = json.loads((WORK / "noaa" / "Current.json").read_text(encoding="utf-8"))
        rows = noaa.get("forecast") if isinstance(noaa, dict) else None
        if isinstance(rows, list) and rows:
            row0 = rows[0] if isinstance(rows[0], dict) else {}
            nsec = dict(kb.get("noaa") or {})
            nsec["next_period"] = {
                "name": row0.get("name"),
                "temperature": row0.get("temperature"),
                "unit": row0.get("unit"),
                "shortForecast": row0.get("shortForecast"),
                "windSpeed": row0.get("windSpeed"),
                "windDirection": row0.get("windDirection"),
            }
            kb["noaa"] = nsec
    except Exception:
        pass

    wipe = ETC / "last-wipe.json"
    if wipe.is_file():
        try:
            kb["packer"] = {
                **dict(kb.get("packer") or {}),
                "last_wipe": json.loads(wipe.read_text(encoding="utf-8")),
            }
        except Exception:
            pass

    if write_back:
        return write(kb)
    return kb


def touch_service(name: str, meta: dict[str, Any]) -> dict[str, Any]:
    """Convenience for pollers: update one section from a Current.meta dict."""
    slim = {
        "ok": meta.get("ok"),
        "updated_at": meta.get("updated_at") or now_hst().isoformat(),
        "current": meta.get("current"),
        "bytes": meta.get("bytes"),
        "sha256_16": meta.get("sha256_16"),
        "source": meta.get("source") or meta.get("url"),
    }
    for k, v in meta.items():
        if k in slim or k in {"detail", "raw"}:
            continue
        if isinstance(v, (str, int, float, bool)) or v is None:
            slim[k] = v
    return update_section(name, slim)
