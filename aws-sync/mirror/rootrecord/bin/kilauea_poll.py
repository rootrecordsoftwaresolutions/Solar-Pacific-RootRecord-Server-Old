#!/usr/bin/env python3
"""RootRecord Kīlauea poller — USGS + HVO; AWS keeps only Current.* (overwrite)."""
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
from common import UA, WORK, ensure_dirs, load_dotenv, now_hst, sleep_until, write_json

log = logging.getLogger("rr.kilauea")
INTERVAL_S = float(__import__("os").environ.get("RR_KILAUEA_INTERVAL_S") or 300.0)
USGS_QUAKE_URL = (
    "https://earthquake.usgs.gov/fdsnws/event/1/query"
    "?format=geojson&minmagnitude=1&maxradiuskm=150"
    "&latitude=19.421&longitude=-155.287&orderby=time&limit=20"
)
HANS_LIST = "https://volcanoes.usgs.gov/hans-public/"
HANS_NOTICE = "https://volcanoes.usgs.gov/hans-public/notice/{id}"
DEST = WORK / "kilauea"
HVO_UA = {**UA, "User-Agent": "RootRecord/1.0 (https://rootrecord.cloud; aws-kilauea)"}


def _strip_html(html: str) -> str:
    text = re.sub(r"<script[^>]*>.*?</script>", " ", html, flags=re.S | re.I)
    text = re.sub(r"<style[^>]*>.*?</style>", " ", text, flags=re.S | re.I)
    text = re.sub(r"<[^>]+>", "\n", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    return text


def _fetch_hvo(client: httpx.Client) -> tuple[str, str]:
    r = client.get(HANS_LIST)
    r.raise_for_status()
    ids = re.findall(r"DOI-USGS-HVO-[\dT:+\-]+", r.text)
    if not ids:
        return "", ""
    notice_id = ids[0]
    r2 = client.get(HANS_NOTICE.format(id=notice_id))
    r2.raise_for_status()
    text = _strip_html(r2.text)
    m = re.search(r"(KĪLAUEA|Kilauea).*", text, re.S)
    if m:
        text = m.group(0)[:4000]
    return notice_id, text


def _infer_alert_level(hvo_text: str) -> str:
    low = (hvo_text or "").lower()
    m = re.search(r"current volcano alert level:\s*(warning|watch|advisory|normal)", low)
    if m:
        return m.group(1)
    m = re.search(r"current aviation color code:\s*(red|orange|yellow|green)", low)
    if m:
        return {"red": "warning", "orange": "watch", "yellow": "advisory", "green": "normal"}[m.group(1)]
    if re.search(r"not erupting|is paused|eruption (is )?paused|currently paused", low):
        return "advisory" if ("advisory" in low or "yellow" in low) else "normal"
    if re.search(r"\bis erupting\b", low) or "aviation color code red" in low:
        return "warning"
    if "watch" in low or "orange" in low:
        return "watch"
    if "advisory" in low or "yellow" in low:
        return "advisory"
    return "normal"


def poll_once() -> dict:
    load_dotenv()
    ensure_dirs()
    DEST.mkdir(parents=True, exist_ok=True)
    with httpx.Client(timeout=30.0, headers=HVO_UA, follow_redirects=True) as client:
        usgs_raw = client.get(USGS_QUAKE_URL)
        usgs_raw.raise_for_status()
        usgs = usgs_raw.json() if usgs_raw.content else {}
        features = usgs.get("features") if isinstance(usgs, dict) else []
        if not isinstance(features, list):
            features = []
        notice_id, hvo_text = "", ""
        hvo_err = None
        try:
            notice_id, hvo_text = _fetch_hvo(client)
        except Exception as exc:
            hvo_err = str(exc)[:200]

    alert = _infer_alert_level(hvo_text)
    erupting = alert == "warning" and not re.search(
        r"not erupting|is paused|eruption (is )?paused", (hvo_text or "").lower()
    )
    payload = {
        "ok": True,
        "updated_at": now_hst().isoformat(),
        "source": {
            "usgs": USGS_QUAKE_URL,
            "hvo_list": HANS_LIST,
        },
        "notice_id": notice_id,
        "alert_level": alert,
        "erupting": bool(erupting),
        "usgs_event_count": len(features),
        "usgs_top": [
            {
                "mag": (f.get("properties") or {}).get("mag"),
                "place": (f.get("properties") or {}).get("place"),
                "time": (f.get("properties") or {}).get("time"),
                "type": (f.get("properties") or {}).get("type"),
            }
            for f in features[:8]
            if isinstance(f, dict)
        ],
        "hvo_excerpt": (hvo_text or "")[:2500],
        "hvo_error": hvo_err,
    }
    raw = json.dumps(payload, indent=2, sort_keys=True).encode("utf-8")
    (DEST / "Current.json").write_bytes(raw)
    digest = hashlib.sha256(raw).hexdigest()[:16]
    meta = {
        "ok": True,
        "current": "Current.json",
        "bytes": len(raw),
        "sha256_16": digest,
        "notice_id": notice_id,
        "alert_level": alert,
        "erupting": bool(erupting),
        "usgs_event_count": len(features),
        "updated_at": now_hst().isoformat(),
    }
    write_json(DEST / "Current.meta.json", meta)
    try:
        import automation_kb

        automation_kb.touch_service("kilauea", meta)
        # richer crumb for automations
        automation_kb.update_section(
            "kilauea",
            {
                "notice_id": notice_id,
                "alert_level": alert,
                "erupting": bool(erupting),
                "usgs_event_count": len(features),
                "hvo_excerpt_len": len(hvo_text or ""),
            },
        )
    except Exception:
        pass
    return meta


def main() -> None:
    load_dotenv()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("kilauea poller interval=%ss", INTERVAL_S)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            log.info(
                "kilauea ok alert=%s erupting=%s usgs=%s notice=%s",
                meta.get("alert_level"),
                meta.get("erupting"),
                meta.get("usgs_event_count"),
                meta.get("notice_id"),
            )
        except Exception as exc:
            log.warning("kilauea poll failed: %s", exc)
            write_json(
                DEST / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
            try:
                import automation_kb

                automation_kb.touch_service(
                    "kilauea",
                    {"ok": False, "detail": str(exc)[:200], "updated_at": now_hst().isoformat()},
                )
            except Exception:
                pass
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
