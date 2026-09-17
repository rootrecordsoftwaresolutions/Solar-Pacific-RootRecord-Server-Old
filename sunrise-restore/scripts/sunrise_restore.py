"""After night sleep ends, play reconnect then burst-collect."""
from __future__ import annotations

import json
import logging
from pathlib import Path

log = logging.getLogger("ava.sunrise_restore")

FLAG = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state" / "sunrise-restore.json"


def _load() -> dict:
    if not FLAG.is_file():
        return {}
    try:
        raw = json.loads(FLAG.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return raw if isinstance(raw, dict) else {}


def _clear() -> None:
    payload = {**_load(), "pending": False}
    FLAG.parent.mkdir(parents=True, exist_ok=True)
    FLAG.write_text(json.dumps(payload, indent=2), encoding="utf-8")


async def maybe_run() -> dict:
    st = _load()
    if not st.get("pending"):
        return {"ok": True, "skipped": True, "reason": "not_pending"}
    try:
        from apps.core.services import voice_events

        await voice_events.announce("battery_reconnect", cooldown_s=0)
        await voice_events.announce("phrase_all_systems_running", cooldown_s=0)
    except Exception as e:
        log.warning("reconnect clip skip: %s", e)
    try:
        from apps.core.crons.since_last_fire import solar_weather

        await solar_weather.live_snapshot()
    except Exception as e:
        log.warning("sunrise ecoflow burst skip: %s", e)
    try:
        from apps.core.services import weather as noaa

        await noaa.run()
    except Exception as e:
        log.debug("sunrise noaa skip: %s", e)
    try:
        from apps.core.services import kilauea

        await kilauea.run()
    except Exception as e:
        log.debug("sunrise kilauea skip: %s", e)
    try:
        from apps.core.services.hybrid_reports import update_hybrid_daily_report

        update_hybrid_daily_report()
    except Exception as e:
        log.warning("sunrise hybrid skip: %s", e)
    try:
        from apps.core.crons.on_time import morning_report, overnight

        await overnight.run()
        await morning_report.run()
    except Exception as e:
        log.info("sunrise reports skip: %s", e)
    _clear()
    return {"ok": True, "ran": True}
