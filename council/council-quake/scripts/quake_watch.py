"""Carly posts each new USGS quake with her spoken WAV attached. Deduped."""
from __future__ import annotations

import json
import time
from datetime import datetime, timezone
from typing import Any
from zoneinfo import ZoneInfo

from .config import CONFIG_DIR

HST = ZoneInfo("Pacific/Honolulu")
PATH = CONFIG_DIR / "quake-watch.json"
MIN_FETCH_S = 90
ISLAND_MIN_MAG = 2.0
GLOBAL_MIN_MAG = 5.0
MAX_POSTS_PER_TICK = 4
MAX_SEEN = 400


def _now() -> int:
    return int(time.time())


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> None:
    PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def _fmt_time_ms(ms: object) -> str:
    try:
        ts = int(ms) / 1000.0
    except (TypeError, ValueError):
        return "time unknown"
    dt = datetime.fromtimestamp(ts, tz=timezone.utc).astimezone(HST)
    hour = dt.strftime("%I").lstrip("0") or "12"
    ampm = dt.strftime("%p")
    return f"{dt.strftime('%b')} {dt.day}, {hour}:{dt.strftime('%M')} {ampm} HST"


def _mag_s(q: dict[str, Any]) -> str:
    mag = q.get("mag")
    try:
        return f"{float(mag):.1f}"
    except (TypeError, ValueError):
        return ""


def format_quake(q: dict[str, Any], *, scope: str) -> str:
    mag_s = _mag_s(q) or "—"
    place = str(q.get("place") or "location unknown").strip()
    when = _fmt_time_ms(q.get("time"))
    qid = str(q.get("id") or "").strip()
    where = "Hawaii region" if scope == "island" else "worldwide"
    lines = [
        f"Earthquake — USGS ({where})",
        f"M {mag_s} · {place}",
        when,
    ]
    depth = q.get("depth_km")
    try:
        d = float(depth)
        lines.append(f"Depth {d:g} km")
    except (TypeError, ValueError):
        pass
    if qid:
        lines.append(f"https://earthquake.usgs.gov/earthquakes/eventpage/{qid}")
    return "\n".join(lines)


def spoken_quake(q: dict[str, Any], *, scope: str) -> str:
    mag_s = _mag_s(q) or "unknown"
    place = str(q.get("place") or "location unknown").strip()
    where = "Hawaii" if scope == "island" else "worldwide"
    bits = [
        "Earthquake.",
        f"U. S. Geological Survey {where}.",
        f"Magnitude {mag_s}.",
        f"{place}.",
    ]
    depth = q.get("depth_km")
    try:
        d = float(depth)
        bits.append(f"Depth {d:g} kilometers.")
    except (TypeError, ValueError):
        pass
    when = _fmt_time_ms(q.get("time"))
    if when and when != "time unknown":
        bits.append(when.replace(" HST", " Hawaiian Standard Time") + ".")
    return " ".join(bits)


def deliver_quake(q: dict[str, Any], *, scope: str) -> dict[str, Any]:
    """Carly Nova WAV plus the matching Telegram notice."""
    spoken = spoken_quake(q, scope=scope)
    text = format_quake(q, scope=scope)
    wav = None
    qid = str(q.get("id") or "new").replace("/", "_")[:80]
    try:
        from apps.core import config
        from apps.voice.speakers import speak_report

        dest = config.GENERATED_DIR / f"quake-{qid}.wav"
        dest.parent.mkdir(parents=True, exist_ok=True)
        built = speak_report("earthquake", spoken, dest)
        if built.get("ok") and dest.is_file() and dest.stat().st_size > 0:
            wav = dest
    except Exception:
        wav = None
    try:
        from apps.council.report_cast import notify_report

        lines = text.split("\n")
        title = " — ".join(lines[:2])[:200]
        return notify_report(
            "earthquake",
            transcript=text,
            audio=wav,
            title=title,
        )
    except Exception as e:
        from .notify import post

        extra = post("carly", text)
        extra["detail"] = str(e)[:200]
        extra["audio"] = bool(wav)
        return extra


def _wanted(q: dict[str, Any], *, scope: str) -> bool:
    try:
        mag = float(q.get("mag"))
    except (TypeError, ValueError):
        return False
    if scope == "island":
        return mag >= ISLAND_MIN_MAG
    return mag >= GLOBAL_MIN_MAG


def process_feed(feed: dict[str, Any] | None) -> dict[str, Any]:
    data = _load()
    data["fetched_at"] = _now()
    if not isinstance(feed, dict):
        data["last_error"] = "no feed"
        _save(data)
        return {"ok": False, "detail": "no feed"}

    seen = [str(x) for x in (data.get("seen") or []) if str(x).strip()]
    seen_set = set(seen)
    seeded = bool(data.get("seeded"))
    posted: list[str] = []
    candidates: list[tuple[str, dict[str, Any]]] = []
    for scope in ("island", "global"):
        for q in feed.get(scope) or []:
            if not isinstance(q, dict):
                continue
            qid = str(q.get("id") or "").strip()
            if not qid or not _wanted(q, scope=scope):
                continue
            candidates.append((scope, q))

    if not seeded:
        for _, q in candidates:
            qid = str(q.get("id") or "")
            if qid and qid not in seen_set:
                seen.append(qid)
                seen_set.add(qid)
        data["seen"] = seen[-MAX_SEEN:]
        data["seeded"] = True
        _save(data)
        return {"ok": True, "seeded": True, "seen": len(seen_set)}

    for scope, q in candidates:
        qid = str(q.get("id") or "")
        if qid in seen_set:
            continue
        if len(posted) >= MAX_POSTS_PER_TICK:
            break
        result = deliver_quake(q, scope=scope)
        seen.append(qid)
        seen_set.add(qid)
        posted.append(qid)
        if not result.get("ok"):
            data["last_error"] = str(result.get("detail") or "post failed")
    data["seen"] = seen[-MAX_SEEN:]
    data["last_posted"] = posted
    _save(data)
    return {"ok": True, "posted": posted, "count": len(posted)}


def should_fetch(*, force: bool = False) -> bool:
    if force:
        return True
    last = int(_load().get("fetched_at") or 0)
    return (not last) or (_now() - last >= MIN_FETCH_S)


async def tick_async(*, force: bool = False) -> dict[str, Any]:
    if not should_fetch(force=force):
        return {"ok": True, "skipped": True, "detail": "fresh"}
    from apps.core.services.obs_desk_data import _fetch_quakes

    feed = await _fetch_quakes()
    return process_feed(feed)


def tick(*, force: bool = False) -> dict[str, Any]:
    if not should_fetch(force=force):
        return {"ok": True, "skipped": True, "detail": "fresh"}
    import asyncio

    from apps.core.services.obs_desk_data import _fetch_quakes

    try:
        asyncio.get_running_loop()
    except RuntimeError:
        feed = asyncio.run(_fetch_quakes())
        return process_feed(feed)
    return {"ok": True, "skipped": True, "detail": "use tick_async"}
