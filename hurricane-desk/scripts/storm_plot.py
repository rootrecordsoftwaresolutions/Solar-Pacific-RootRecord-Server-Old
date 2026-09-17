"""Text plot: storm vs Hawaiʻi islands. File facts only. No maps, no invented positions."""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

STATE = Path.home() / ".ollama" / "skills" / "state" / "store"
HAWAII_THREAT_NM = 800
HAWAII = {
    "Honolulu": (21.3069, -157.8583),
    "Hilo": (19.7297, -155.0900),
    "Līhuʻe": (21.9811, -159.3711),
    "Kona": (19.6390, -155.9969),
}


def _load(name: str) -> dict[str, Any]:
    path = STATE / name
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _compass(deg: float) -> str:
    names = ("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    idx = int((deg + 22.5) // 45) % 8
    return names[idx]


def _bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360.0) % 360.0


def _nearest_storm(storms: list[Any]) -> dict[str, Any] | None:
    best: dict[str, Any] | None = None
    best_nm = 9e9
    for s in storms:
        if not isinstance(s, dict):
            continue
        nm = s.get("nearest_hawaii_nm")
        try:
            nmi = float(nm) if nm is not None else None
        except (TypeError, ValueError):
            nmi = None
        if nmi is None:
            continue
        if nmi < best_nm:
            best_nm = nmi
            best = s
    return best


def _mark(compass: str) -> str:
    raw = (compass or "").strip().lower()
    aliases = {
        "n": "N",
        "north": "N",
        "ne": "NE",
        "northeast": "NE",
        "e": "E",
        "east": "E",
        "se": "SE",
        "southeast": "SE",
        "s": "S",
        "south": "S",
        "sw": "SW",
        "southwest": "SW",
        "w": "W",
        "west": "W",
        "nw": "NW",
        "northwest": "NW",
    }
    c = aliases.get(raw, raw.upper())
    n = "*" if c in {"N", "NE", "NW"} else "|"
    e = "*" if c in {"E", "NE", "SE"} else "-"
    s = "*" if c in {"S", "SE", "SW"} else "|"
    w = "*" if c in {"W", "NW", "SW"} else "-"
    return f"        N\n        {n}\n {w}------+------{e} E     + = Hawaiʻi\n        {s}\n        S"


def plot_text() -> str:
    desk = _load("hurricane-desk.json")
    bundle = _load("hurricanes.json")
    hi = desk.get("hawaii") if isinstance(desk.get("hawaii"), dict) else {}
    storms = bundle.get("storms") if isinstance(bundle.get("storms"), list) else []
    near = _nearest_storm(storms)
    name = str(hi.get("label") or hi.get("name") or (near or {}).get("label") or "No mapped storm")
    try:
        nmi = float(hi.get("nm")) if hi.get("nm") is not None else None
    except (TypeError, ValueError):
        nmi = None
    if nmi is None and near is not None:
        try:
            nmi = float(near.get("nearest_hawaii_nm"))
        except (TypeError, ValueError):
            nmi = None
    island = str(hi.get("island") or "Līhuʻe")
    compass = str(hi.get("bearing") or "")
    lat = lon = None
    if near:
        try:
            lat = float(near["lat"]) if near.get("lat") is not None else None
            lon = float(near["lon"]) if near.get("lon") is not None else None
        except (TypeError, ValueError):
            lat = lon = None
        if not compass and lat is not None and lon is not None:
            pos = HAWAII.get(island) or HAWAII["Līhuʻe"]
            compass = _compass(_bearing(pos[0], pos[1], lat, lon))
    trop = hi.get("nws_tropical") or hi.get("alerts") or []
    threat = bool(trop) or (nmi is not None and nmi < HAWAII_THREAT_NM)
    lines = [
        "Storm plot vs Hawaiʻi (file facts). + is the islands. History and RAMMB forecast are on-file products, not a landfall call.",
        _mark(compass),
    ]
    pos_s = ""
    if lat is not None and lon is not None:
        ns = "N" if lat >= 0 else "S"
        ew = "E" if lon >= 0 else "W"
        pos_s = f" {abs(lat):.1f}{ns} {abs(lon):.1f}{ew}."
    dist = f" {int(nmi)} nmi" if nmi is not None else " distance No data"
    lines.append(f"{name}.{pos_s}{dist} {compass} of {island}.".replace("  ", " "))
    if near and isinstance(near.get("hawaii_nm"), dict):
        bits = []
        for isle, d in sorted(near["hawaii_nm"].items(), key=lambda kv: float(kv[1] or 9e9)):
            bits.append(f"{isle} {int(float(d))} nmi")
        if bits:
            lines.append("Islands: " + "; ".join(bits) + ".")
    basin = str((near or {}).get("basin_name") or (near or {}).get("basin") or hi.get("basin") or "")
    if basin:
        lines.append(f"Basin: {basin}.")
    region = str((near or {}).get("region_name") or hi.get("region_name") or "")
    if region:
        lines.append(f"Region: {region}.")
    move = str((near or {}).get("movement_compass") or hi.get("movement_compass") or "")
    approach = str((near or {}).get("hawaii_approach") or hi.get("hawaii_approach") or "")
    try:
        mkt = (near or {}).get("movement_kt")
        if mkt is None:
            mkt = hi.get("movement_kt")
        mkt_i = int(round(float(mkt))) if mkt is not None else None
    except (TypeError, ValueError):
        mkt_i = None
    if move:
        ktbit = f" {mkt_i} kt" if mkt_i is not None else ""
        vs = {"toward": "toward Hawaiʻi", "away": "away from Hawaiʻi", "abeam": "abeam of Hawaiʻi"}.get(approach, "vs Hawaiʻi unknown")
        lines.append(f"Motion: {move}{ktbit} — {vs}.")
    hist = (near or {}).get("track_history") if isinstance((near or {}).get("track_history"), list) else []
    if len(hist) >= 2:
        a, b = hist[0], hist[-1]
        try:
            lines.append(
                f"History: {abs(float(a['lat'])):.1f}{'N' if float(a['lat'])>=0 else 'S'} "
                f"{abs(float(a['lon'])):.1f}{'E' if float(a['lon'])>=0 else 'W'} → "
                f"{abs(float(b['lat'])):.1f}{'N' if float(b['lat'])>=0 else 'S'} "
                f"{abs(float(b['lon'])):.1f}{'E' if float(b['lon'])>=0 else 'W'} "
                f"({len(hist)} RAMMB fixes)."
            )
        except (TypeError, ValueError, KeyError):
            pass
    fc = str((near or {}).get("forecast_summary") or hi.get("forecast_summary") or "")
    if fc:
        lines.append(fc)
    if threat:
        lines.append("Hawaiʻi threat: YES — NWS watch or inside 800 nmi.")
    else:
        lines.append(
            "Hawaiʻi threat: NO. ≥800 nmi and no Honolulu tropical watch. "
            "West of Kauaʻi is Asia/Japan, not toward the islands. Do not alarm."
        )
    return "\n".join(lines)


def prompt_line(*, cap: int = 280) -> str:
    blob = " ".join(plot_text().split())
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def main() -> int:
    print(plot_text())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
