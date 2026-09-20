"""Standalone local Weather processor for NWS forecast and Hawaii alerts."""

from __future__ import annotations

import hashlib
import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx

log = logging.getLogger("rootrecord.weather")

HST = ZoneInfo("Pacific/Honolulu")
REPORTS_ROOT = Path.home() / ".ollama" / "skills" / "hybrid-reports" / "store" / "Reports"
NWS_POINT_URL = "https://api.weather.gov/points/19.5429,-155.0372"
NWS_ALERTS_URL = "https://api.weather.gov/alerts/active?area=HI"
_last_hash: str = ""


def _ordinal(day: int) -> str:
    if 10 <= day % 100 <= 20:
        suffix = "th"
    else:
        suffix = {1: "st", 2: "nd", 3: "rd"}.get(day % 10, "th")
    return f"{day}{suffix}"


def daily_report_dir(now: datetime | None = None) -> Path:
    now = now or datetime.now(HST)
    return REPORTS_ROOT / f"{now:%Y}" / f"{now:%B}" / f"{now:%B} {_ordinal(now.day)}, {now:%Y}"


def weather_report_path(now: datetime | None = None) -> Path:
    now = now or datetime.now(HST)
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H")
    return daily_report_dir(now) / f"nws-weather-{stamp}.md"


def _weather_content(periods: list[dict], alerts: list[dict]) -> str:
    lines = ["# NWS Weather", ""]
    if alerts:
        lines.extend([f"## Active HI Alerts ({len(alerts)})", ""])
        for alert in alerts[:5]:
            lines.extend([
                f"**{alert['event']}** — {alert['severity']} / {alert['urgency']}",
                alert["headline"],
                f"_{alert['areas']}_",
                "",
            ])
    else:
        lines.extend(["## No active HI alerts.", ""])
    if periods:
        lines.extend(["## Forecast", ""])
        for period in periods[:4]:
            lines.extend([
                f"### {period.get('name', '?')}",
                f"{period.get('temperature', '?')}°{period.get('temperatureUnit', 'F')} — "
                f"{period.get('shortForecast', '?')}",
                period.get("detailedForecast", ""),
                "",
            ])
    return "\n".join(lines)


async def run() -> dict:
    global _last_hash
    log.info("Weather processor running %s", datetime.now(timezone.utc).isoformat())
    try:
        async with httpx.AsyncClient(
            timeout=20,
            headers={"User-Agent": "AvaIvy/2.0 rootmc.net"},
            follow_redirects=True,
        ) as client:
            response = await client.get(NWS_POINT_URL)
            periods: list[dict] = []
            if response.status_code == 200:
                forecast_url = response.json().get("properties", {}).get("forecast")
                if forecast_url:
                    forecast = await client.get(forecast_url)
                    if forecast.status_code == 200:
                        periods = forecast.json().get("properties", {}).get("periods", [])
            else:
                log.warning("NWS point failed: %s", response.status_code)

            alerts: list[dict] = []
            alert_response = await client.get(NWS_ALERTS_URL)
            if alert_response.status_code == 200:
                for feature in alert_response.json().get("features", []):
                    props = feature.get("properties", {})
                    alerts.append({
                        "event": props.get("event", "Unknown"),
                        "headline": props.get("headline", ""),
                        "areas": props.get("areaDesc", ""),
                        "severity": props.get("severity", ""),
                        "urgency": props.get("urgency", ""),
                    })
            else:
                log.warning("NWS alerts fetch failed: %s", alert_response.status_code)

        if not periods:
            log.warning("Weather: no forecast periods; no report written")
            return {"ok": False, "detail": "no_forecast_periods"}

        content = _weather_content(periods, alerts)
        fingerprint = json.dumps({
            "alerts": [(a.get("event"), a.get("headline")) for a in alerts[:8]],
            "forecast": [(p.get("name"), p.get("temperature"), p.get("shortForecast")) for p in periods[:4]],
        }, sort_keys=True)
        content_hash = hashlib.md5(fingerprint.encode()).hexdigest()
        path = weather_report_path()
        if content_hash == _last_hash and path.is_file():
            return {"ok": True, "detail": "unchanged", "path": str(path)}

        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        _last_hash = content_hash
        log.info("Weather report written: %s", path)

        critical = [a for a in alerts if a["severity"].lower() in {"extreme", "severe"}]
        if critical:
            from apps.core.services import reports
            alert_text = "\n".join(
                f"⚠️ **NWS ALERT** — {a['event']}: {a['headline']}" for a in critical[:3]
            )
            alert_path = daily_report_dir() / f"nws-weather-alert-{datetime.now(timezone.utc):%Y-%m-%dT%H%M}.md"
            alert_path.parent.mkdir(parents=True, exist_ok=True)
            alert_path.write_text(alert_text + "\n", encoding="utf-8")
            posted = await reports.publish("weather", alert_text, channel="ava_home")
            if not posted.get("ok"):
                reports.queue_public_draft("weather", alert_text, source="weather")
        return {"ok": True, "path": str(path), "alerts": len(alerts), "periods": len(periods)}
    except Exception as exc:
        log.exception("Weather processor failed")
        return {"ok": False, "detail": str(exc)}


__all__ = ["REPORTS_ROOT", "daily_report_dir", "run", "weather_report_path"]
