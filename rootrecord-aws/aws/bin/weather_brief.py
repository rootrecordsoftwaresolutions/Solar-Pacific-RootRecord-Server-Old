#!/usr/bin/env python3
"""Build a short Hawaiʻi weather brief from AWS work/weather + work/noaa Current.*"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import WORK, now_hst


def _alerts(limit: int = 4) -> list[str]:
    path = WORK / "weather" / "Current.json"
    if not path.is_file():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    lines: list[str] = []
    seen: set[str] = set()
    for feat in data.get("features") or []:
        props = (feat or {}).get("properties") or {}
        event = str(props.get("event") or "").strip()
        headline = str(props.get("headline") or props.get("description") or "").strip()
        headline = " ".join(headline.split())
        if not event and not headline:
            continue
        key = event.lower()
        if key in seen:
            continue
        seen.add(key)
        if headline and event.lower() not in headline.lower():
            lines.append(f"• {event}: {headline[:180]}")
        elif headline:
            lines.append(f"• {headline[:200]}")
        else:
            lines.append(f"• {event}")
        if len(lines) >= limit:
            break
    return lines


def _forecast(limit: int = 3) -> list[str]:
    path = WORK / "noaa" / "Current.json"
    if not path.is_file():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    rows = data.get("forecast") if isinstance(data, dict) else None
    if not isinstance(rows, list):
        return []
    lines: list[str] = []
    for row in rows[:limit]:
        if not isinstance(row, dict):
            continue
        name = str(row.get("name") or "").strip()
        temp = row.get("temperature")
        unit = str(row.get("unit") or "F").strip()
        short = str(row.get("shortForecast") or "").strip()
        wind = str(row.get("windSpeed") or "").strip()
        wdir = str(row.get("windDirection") or "").strip()
        bits = [name] if name else []
        if temp is not None:
            bits.append(f"{temp}°{unit}")
        if short:
            bits.append(short)
        if wind:
            bits.append(f"wind {wdir} {wind}".strip())
        if bits:
            lines.append("• " + " — ".join(bits))
    return lines


def format_brief() -> str:
    alerts = _alerts()
    forecast = _forecast()
    stamp = now_hst().strftime("%Y-%m-%d %H:%M HST")
    parts = [f"Latest Hawaiʻi weather ({stamp})"]
    if alerts:
        parts.append("Alerts:")
        parts.extend(alerts)
    else:
        parts.append("Alerts: none active in Current.json")
    if forecast:
        parts.append("Fern Forest / Big Island forecast:")
        parts.extend(forecast)
    else:
        parts.append("Forecast: waiting on NOAA Current.json")
    text = "\n".join(parts).strip()
    return text[:3500]


def main() -> int:
    print(format_brief())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
