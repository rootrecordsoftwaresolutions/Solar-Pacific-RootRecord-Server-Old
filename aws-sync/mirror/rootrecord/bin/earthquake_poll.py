#!/usr/bin/env python3
"""RootRecord earthquake poller — USGS; AWS keeps only Current.* (overwrite)."""
from __future__ import annotations

import hashlib
import json
import logging
import sys
import time
from pathlib import Path
from urllib.parse import urlencode

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import UA, WORK, ensure_dirs, now_hst, sleep_until, write_json

log = logging.getLogger("rr.earthquake")
USGS = "https://earthquake.usgs.gov/fdsnws/event/1/query"
INTERVAL_S = 30.0
HAWAII_BBOX = {
    "minlatitude": 18.5,
    "maxlatitude": 22.5,
    "minlongitude": -160.5,
    "maxlongitude": -154.5,
}


def _fetch(client: httpx.Client, params: dict) -> bytes:
    q = {**params, "format": "geojson", "orderby": "time"}
    r = client.get(f"{USGS}?{urlencode(q)}")
    r.raise_for_status()
    return r.content


def _count(raw: bytes) -> int:
    try:
        data = json.loads(raw)
        feats = data.get("features") if isinstance(data, dict) else []
        return len(feats) if isinstance(feats, list) else 0
    except json.JSONDecodeError:
        return -1


def poll_once() -> dict:
    ensure_dirs()
    dest = WORK / "earthquakes"
    with httpx.Client(timeout=30.0, headers=UA) as client:
        hi = _fetch(client, {**HAWAII_BBOX, "minmagnitude": 1.0, "limit": 100})
        glob = _fetch(client, {"minmagnitude": 4.5, "limit": 100})
    (dest / "Current-hawaii.json").write_bytes(hi)
    (dest / "Current-global.json").write_bytes(glob)
    meta = {
        "ok": True,
        "hawaii_count": _count(hi),
        "global_count": _count(glob),
        "hawaii_sha256_16": hashlib.sha256(hi).hexdigest()[:16],
        "global_sha256_16": hashlib.sha256(glob).hexdigest()[:16],
        "current_hawaii": "Current-hawaii.json",
        "current_global": "Current-global.json",
        "updated_at": now_hst().isoformat(),
    }
    write_json(dest / "Current.meta.json", meta)
    try:
        import automation_kb
        automation_kb.touch_service("earthquake", meta)
    except Exception:
        pass
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("RootRecord earthquake poller Current.* interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info("quakes ok hi=%s global=%s", meta.get("hawaii_count"), meta.get("global_count"))
        except Exception as exc:
            log.warning("earthquake poll failed: %s", exc)
            write_json(
                WORK / "earthquakes" / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
