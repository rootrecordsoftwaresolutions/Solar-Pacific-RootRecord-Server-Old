#!/usr/bin/env python3
"""RootRecord hurricane / global tropical poller — NHC + RAMMB + JTWC → Current.json."""
from __future__ import annotations

import hashlib
import json
import logging
import re
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import UA, WORK, ensure_dirs, now_hst, sleep_until, write_json

log = logging.getLogger("rr.hurricane")
INTERVAL_S = 300.0  # 5 min — tropical boards
NHC_URL = "https://www.nhc.noaa.gov/CurrentStorms.json"
RAMMB_URL = "https://rammb-data.cira.colostate.edu/tc_realtime/"
JTWC_ABPW = "https://www.metoc.navy.mil/jtwc/products/abpwweb.txt"
JTWC_ABIO = "https://www.metoc.navy.mil/jtwc/products/abioweb.txt"
RR_UA = {**UA, "User-Agent": "RootRecord/1.0 (https://rootrecord.cloud; hurricane)"}


def _grab(client: httpx.Client, url: str) -> tuple[bool, str | bytes]:
    try:
        r = client.get(url)
        r.raise_for_status()
        ctype = r.headers.get("content-type", "")
        if "json" in ctype or url.endswith(".json"):
            return True, r.text
        return True, r.text
    except Exception as exc:
        return False, str(exc)[:300]


def _parse_nhc(text: str) -> list[dict]:
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return []
    storms = []
    active = data.get("activeStorms") if isinstance(data, dict) else None
    if not isinstance(active, list):
        # some feeds nest differently
        active = data if isinstance(data, list) else []
    for s in active:
        if not isinstance(s, dict):
            continue
        storms.append(
            {
                "id": s.get("id") or s.get("binNumber") or s.get("name"),
                "name": s.get("name") or s.get("Name"),
                "basin": s.get("basin") or s.get("Basin"),
                "classification": s.get("classification") or s.get("Classification"),
                "intensityMph": s.get("intensityMph") or s.get("intensity"),
                "movement": s.get("movement"),
                "pressureMb": s.get("pressureMb") or s.get("pressure"),
                "latitude": s.get("latitude") or s.get("lat"),
                "longitude": s.get("longitude") or s.get("lon"),
                "source": "NHC",
            }
        )
    return storms


def _jtwc_mentions(text: str) -> list[str]:
    # Crude: lines with TYPHOON / STORM / INVEST
    out = []
    for line in text.splitlines():
        u = line.upper()
        if any(k in u for k in ("TYPHOON", "HURRICANE", "TROPICAL STORM", "INVEST", "TD ", "TS ")):
            out.append(line.strip()[:200])
    return out[:40]


def poll_once() -> dict:
    ensure_dirs()
    dest = WORK / "hurricane"
    dest.mkdir(parents=True, exist_ok=True)
    with httpx.Client(timeout=45.0, headers=RR_UA, follow_redirects=True) as client:
        ok_n, nhc = _grab(client, NHC_URL)
        ok_r, rammb = _grab(client, RAMMB_URL)
        ok_p, abpw = _grab(client, JTWC_ABPW)
        ok_i, abio = _grab(client, JTWC_ABIO)

    storms = _parse_nhc(nhc if ok_n and isinstance(nhc, str) else "{}")
    payload = {
        "ok": ok_n or ok_r or ok_p or ok_i,
        "updated_at": now_hst().isoformat(),
        "sources": {
            "nhc": ok_n,
            "rammb": ok_r,
            "jtwc_abpw": ok_p,
            "jtwc_abio": ok_i,
        },
        "nhc_storm_count": len(storms),
        "storms": storms,
        "jtwc_abpw_lines": _jtwc_mentions(abpw if ok_p and isinstance(abpw, str) else ""),
        "jtwc_abio_lines": _jtwc_mentions(abio if ok_i and isinstance(abio, str) else ""),
        "rammb_html_bytes": len(rammb) if ok_r and isinstance(rammb, str) else 0,
    }
    raw = json.dumps(payload, indent=2).encode()
    (dest / "Current.json").write_bytes(raw)
    # Keep raw NHC too for local processors
    if ok_n and isinstance(nhc, str):
        (dest / "Current-nhc.json").write_text(nhc, encoding="utf-8")
    digest = hashlib.sha256(raw).hexdigest()[:16]
    meta = {
        "ok": payload["ok"],
        "sha256_16": digest,
        "nhc_storm_count": len(storms),
        "current": "Current.json",
        "updated_at": payload["updated_at"],
        "sources": payload["sources"],
    }
    write_json(dest / "Current.meta.json", meta)
    try:
        import automation_kb
        automation_kb.touch_service("hurricane", meta)
    except Exception:
        pass
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("RootRecord hurricane poller interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info("hurricane ok storms=%s", meta.get("nhc_storm_count"))
        except Exception as exc:
            log.warning("hurricane poll failed: %s", exc)
            write_json(
                WORK / "hurricane" / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
