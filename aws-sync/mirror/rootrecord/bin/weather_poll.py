#!/usr/bin/env python3
"""RootRecord weather poller — NWS Hawaiʻi; AWS keeps only Current.* (overwrite)."""
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

log = logging.getLogger("rr.weather")
ALERTS_URL = "https://api.weather.gov/alerts/active?area=HI"
INTERVAL_S = 60.0


def poll_once() -> dict:
    ensure_dirs()
    dest = WORK / "weather"
    with httpx.Client(timeout=30.0, headers={**UA, "Accept": "application/geo+json"}) as client:
        r = client.get(ALERTS_URL)
        r.raise_for_status()
        body = r.content
    digest = hashlib.sha256(body).hexdigest()[:16]
    # Overwrite only — no accumulation on AWS
    (dest / "Current.json").write_bytes(body)
    try:
        data = json.loads(body)
        features = data.get("features") if isinstance(data, dict) else []
        count = len(features) if isinstance(features, list) else 0
    except json.JSONDecodeError:
        count = -1
    meta = {
        "ok": True,
        "source": ALERTS_URL,
        "bytes": len(body),
        "sha256_16": digest,
        "feature_count": count,
        "current": "Current.json",
        "updated_at": now_hst().isoformat(),
    }
    write_json(dest / "Current.meta.json", meta)
    try:
        import automation_kb
        automation_kb.touch_service("weather", meta)
    except Exception:
        pass
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("RootRecord weather poller Current.* interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info("weather ok features=%s sha=%s", meta.get("feature_count"), meta.get("sha256_16"))
        except Exception as exc:
            log.warning("weather poll failed: %s", exc)
            write_json(
                WORK / "weather" / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
        from common import sleep_until as _su

        _su(INTERVAL_S, started)


if __name__ == "__main__":
    main()
