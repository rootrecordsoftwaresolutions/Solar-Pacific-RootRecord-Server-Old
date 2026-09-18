#!/usr/bin/env python3
"""Build one report prep from the live datapack tree for the next publish mark.

Facts always come from live Current.meta.json files.
When AVA Console is up (FastFlowLM :52625), adds a short public-voice narrative.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import sys
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx

HST = ZoneInfo("Pacific/Honolulu")
log = logging.getLogger("rr.prep")
STORE = Path.home() / ".ollama" / "skills" / "rootrecord-aws" / "store"
LIVE = STORE / "live"
STATE = STORE / "state"
PREP = STORE / "prep"
FLM_URL = (os.environ.get("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/")
CONSOLE_FLAG = Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-console-up"


def next_publish_mark(now: datetime | None = None) -> datetime:
    now = now or datetime.now(HST)
    marks = (0, 15, 30, 45)
    for m in marks:
        cand = now.replace(minute=m, second=0, microsecond=0)
        if cand > now:
            return cand
    return (now + timedelta(hours=1)).replace(minute=0, second=0, microsecond=0)


def _read_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def _latest_meta(subdir: str) -> dict:
    root = LIVE / subdir
    if not root.is_dir():
        return {}
    cands = sorted(
        list(root.glob("*Current.meta.json")) + list(root.glob("Current.meta.json")),
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    for p in cands:
        meta = _read_json(p)
        if meta:
            return meta
    return {}


def _content_fingerprint(root: Path) -> str:
    h = hashlib.sha256()
    if not root.is_dir():
        return h.hexdigest()[:16]
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.name != ".keep":
            h.update(str(path.relative_to(root)).encode())
            h.update(path.read_bytes()[:65536])
    return h.hexdigest()[:16]


def _flm_available() -> bool:
    if not CONSOLE_FLAG.is_file():
        return False
    try:
        r = httpx.get(f"{FLM_URL}/v1/models", timeout=3.0)
        return r.status_code == 200
    except Exception:
        return False


def _ai_narrative(facts: str) -> tuple[str | None, str]:
    """Returns (text, status). Never invents numbers — only rewrites known facts.

    Skipped when RR_PREP_AI=0 or FLM is down. Keeps prep facts-only after NPU pressure.
    """
    if (os.environ.get("RR_PREP_AI") or "1").strip() in ("0", "false", "no"):
        return None, "ai_disabled"
    if not _flm_available():
        return None, "flm_offline"
    messages = [
        {
            "role": "system",
            "content": (
                "You write short Root Record desk notes for Hawaiʻi visitors. "
                "Only use facts given. Short sentences. Plain words. "
                "No repo paths, no env vars, no stack traces. "
                "If a section says ok: False or is missing, say you do not have it. "
                "Do not invent storm names, magnitudes, or temperatures."
            ),
        },
        {
            "role": "user",
            "content": (
                "Turn these desk facts into a 6–10 sentence public briefing. "
                "Lead with what is true and working.\n\n" + facts
            ),
        },
    ]
    try:
        r = httpx.post(
            f"{FLM_URL}/v1/chat/completions",
            json={
                "model": os.environ.get("AVA_FLM_MODEL") or "default",
                "messages": messages,
                "stream": False,
                "max_tokens": 256,
            },
            timeout=60.0,
        )
        if r.status_code != 200:
            return None, f"flm_http_{r.status_code}"
        choices = (r.json() or {}).get("choices") or []
        if not choices:
            return None, "flm_empty"
        text = ((choices[0] or {}).get("message") or {}).get("content") or ""
        text = str(text).strip()
        return (text or None), ("ok" if text else "flm_empty")
    except Exception as exc:
        return None, f"flm_error:{type(exc).__name__}"


def run() -> dict:
    PREP.mkdir(parents=True, exist_ok=True)
    STATE.mkdir(parents=True, exist_ok=True)
    now = datetime.now(HST)
    target = next_publish_mark(now)
    wx = _latest_meta("weather")
    eq = _latest_meta("earthquakes")
    radar = _latest_meta("radar")
    chat = _latest_meta("chatlogs")
    cane = _latest_meta("hurricane")
    noaa = _latest_meta("noaa")
    sysmon = _latest_meta("sysmon")
    fp = _content_fingerprint(LIVE)
    prev = _read_json(STATE / "prep.json")
    changed = fp != prev.get("content_hash")

    radio_url = ""
    if isinstance(sysmon, dict):
        radio_url = str(sysmon.get("radio_public_url") or "")
    url_file = LIVE / "sysmon"
    if not radio_url and url_file.is_dir():
        for p in sorted(url_file.glob("*radio-public.url"), reverse=True):
            radio_url = p.read_text(encoding="utf-8", errors="replace").strip()
            if radio_url:
                break

    facts_lines = [
        f"# RootRecord desk prep — {target.strftime('%Y-%m-%d %H:%M')} HST",
        "",
        f"Prepared at {now.isoformat()}",
        f"Content hash `{fp}`" + (" (changed)" if changed else " (unchanged)"),
        "",
        "## Weather (NWS alerts)",
        f"- ok: {wx.get('ok')} features: {wx.get('feature_count')} updated: {wx.get('updated_at')}",
        "",
        "## NOAA forecast",
        f"- ok: {noaa.get('ok')} periods: {noaa.get('forecast_periods')} alerts: {noaa.get('alert_feature_count')} updated: {noaa.get('updated_at')}",
        "",
        "## Earthquakes",
        f"- ok: {eq.get('ok')} hawaii: {eq.get('hawaii_count')} global: {eq.get('global_count')} updated: {eq.get('updated_at')}",
        "",
        "## Hurricane / tropical",
        f"- ok: {cane.get('ok')} NHC storms: {cane.get('nhc_storm_count')} updated: {cane.get('updated_at')}",
        "",
        "## Radar",
        f"- ok: {radar.get('ok')} bytes: {radar.get('bytes')} updated: {radar.get('updated_at')}",
        "",
        "## Chatlogs",
        f"- ok: {chat.get('ok')} updates_last_poll: {chat.get('updates')} updated: {chat.get('updated_at')}",
        "",
        "## Radio",
        f"- public_url: {radio_url or '(see AWS etc/radio-public.url)'}",
        "",
    ]
    facts = "\n".join(facts_lines) + "\n"
    narrative, ai_status = _ai_narrative(facts)
    body = facts
    if narrative:
        body = facts + "## Briefing\n\n" + narrative.strip() + "\n"

    mark_name = target.strftime("%Y%m%d-%H%M")
    out_md = PREP / f"prep-{mark_name}.md"
    out_md.write_text(body, encoding="utf-8")
    (PREP / "prep-current.md").write_text(body, encoding="utf-8")
    payload = {
        "ok": True,
        "publish_at": target.isoformat(),
        "publish_mark": mark_name,
        "prepared_at": now.isoformat(),
        "content_hash": fp,
        "changed": changed,
        "path": str(out_md),
        "weather_ok": bool(wx.get("ok")),
        "earthquakes_ok": bool(eq.get("ok")),
        "radar_ok": bool(radar.get("ok")),
        "hurricane_ok": bool(cane.get("ok")),
        "noaa_ok": bool(noaa.get("ok")),
        "ai_status": ai_status,
        "ai_used": bool(narrative),
        "radio_public_url": radio_url,
    }
    (STATE / "prep.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    needed = STATE / "prep-needed"
    if needed.exists():
        needed.unlink()
    (STATE / "publish-ready").write_text(mark_name + "\n", encoding="utf-8")
    return payload


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    print(json.dumps(run(), indent=2))


if __name__ == "__main__":
    main()
