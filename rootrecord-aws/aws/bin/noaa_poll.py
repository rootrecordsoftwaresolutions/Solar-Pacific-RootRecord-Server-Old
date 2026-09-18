#!/usr/bin/env python3
"""RootRecord NOAA/NWS forecast poller (Hilo point) → Current.json overwrite."""
from __future__ import annotations

import hashlib
import json
import logging
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import UA, WORK, ensure_dirs, now_hst, sleep_until, write_json

log = logging.getLogger("rr.noaa")
INTERVAL_S = 900.0  # 15 min
POINT_URL = "https://api.weather.gov/points/19.5429,-155.0372"
ALERTS_URL = "https://api.weather.gov/alerts/active?area=HI"
HDR = {**UA, "Accept": "application/geo+json"}


def poll_once() -> dict:
    ensure_dirs()
    dest = WORK / "noaa"
    dest.mkdir(parents=True, exist_ok=True)
    periods: list[dict] = []
    with httpx.Client(timeout=30.0, headers=HDR, follow_redirects=True) as client:
        pr = client.get(POINT_URL)
        pr.raise_for_status()
        forecast_url = (pr.json().get("properties") or {}).get("forecast")
        if forecast_url:
            fr = client.get(forecast_url)
            fr.raise_for_status()
            periods = (fr.json().get("properties") or {}).get("periods") or []
        ar = client.get(ALERTS_URL)
        ar.raise_for_status()
        alerts = ar.json()
    forecast = [
        {
            "name": p.get("name"),
            "temperature": p.get("temperature"),
            "unit": p.get("temperatureUnit"),
            "shortForecast": p.get("shortForecast"),
            "windSpeed": p.get("windSpeed"),
            "windDirection": p.get("windDirection"),
        }
        for p in periods[:8]
        if isinstance(p, dict)
    ]
    payload = {
        "ok": True,
        "updated_at": now_hst().isoformat(),
        "point": POINT_URL,
        "forecast": forecast,
        "alert_feature_count": len(alerts.get("features") or []) if isinstance(alerts, dict) else 0,
    }
    raw = json.dumps(payload, indent=2).encode()
    (dest / "Current.json").write_bytes(raw)
    digest = hashlib.sha256(raw).hexdigest()[:16]
    meta = {
        "ok": True,
        "sha256_16": digest,
        "forecast_periods": len(forecast),
        "alert_feature_count": payload["alert_feature_count"],
        "current": "Current.json",
        "updated_at": payload["updated_at"],
    }
    write_json(dest / "Current.meta.json", meta)
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("RootRecord NOAA poller interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info("noaa ok periods=%s alerts=%s", meta.get("forecast_periods"), meta.get("alert_feature_count"))
        except Exception as exc:
            log.warning("noaa poll failed: %s", exc)
            write_json(
                WORK / "noaa" / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
