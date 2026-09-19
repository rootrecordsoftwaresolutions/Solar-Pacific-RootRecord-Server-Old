#!/usr/bin/env python3
"""RootRecord radar GIF poller — AWS keeps only Current.gif (overwrite)."""
from __future__ import annotations

import hashlib
import logging
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import UA, WORK, ensure_dirs, now_hst, sleep_until, write_json

log = logging.getLogger("rr.radar")
URL = "https://radar.weather.gov/ridge/standard/HAWAII_loop.gif"
INTERVAL_S = 120.0


def poll_once() -> dict:
    ensure_dirs()
    dest = WORK / "radar"
    with httpx.Client(timeout=45.0, headers=UA) as client:
        r = client.get(URL)
        r.raise_for_status()
        data = r.content
    if not data.startswith((b"GIF87a", b"GIF89a")):
        raise ValueError("radar response is not a GIF")
    digest = hashlib.sha256(data).hexdigest()[:16]
    (dest / "Current.gif").write_bytes(data)
    meta = {
        "ok": True,
        "url": URL,
        "bytes": len(data),
        "sha256_16": digest,
        "current": "Current.gif",
        "updated_at": now_hst().isoformat(),
    }
    write_json(dest / "Current.meta.json", meta)
    try:
        import automation_kb
        automation_kb.touch_service("radar", meta)
    except Exception:
        pass
    return meta


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("RootRecord radar poller Current.gif interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info("radar ok bytes=%s sha=%s", meta.get("bytes"), meta.get("sha256_16"))
        except Exception as exc:
            log.warning("radar poll failed: %s", exc)
            write_json(
                WORK / "radar" / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
