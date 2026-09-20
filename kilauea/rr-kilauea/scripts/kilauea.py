"""
Kīlauea / USGS + HVO cron.
Hourly poll. Publish hash lives on disk (scheduler re-execs this file each tick).
Gate on HVO notice + alert level, not USGS microquakes. Unchanged data does not republish.
"""

from __future__ import annotations

from pathlib import Path
import sys
_AVA = Path.home() / "RootRecord" / "Ava-Core"
if str(_AVA) not in sys.path:
    sys.path.insert(0, str(_AVA))

import hashlib
import json
import logging
import re
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

import httpx

log = logging.getLogger("ava.cron.kilauea")

USGS_QUAKE_URL = (
    "https://earthquake.usgs.gov/fdsnws/event/1/query"
    "?format=geojson&minmagnitude=1&maxradiuskm=150"
    "&latitude=19.421&longitude=-155.287&orderby=time&limit=20"
)
HANS_LIST = "https://volcanoes.usgs.gov/hans-public/"
HANS_NOTICE = "https://volcanoes.usgs.gov/hans-public/notice/{id}"
HVO_UA = {"User-Agent": "AvaIvy/2.0 rootmc.net"}

REPORTS_ROOT = Path.home() / ".ollama" / "skills" / "hybrid-reports" / "store" / "Reports"
PUBLISH_NAME = "kilauea-publish.json"

MULTIPLIERS = {
    "normal":   1.0,
    "advisory": 2.0,
    "watch":    2.5,
    "eruption": 3.0,
}


def _daily_report_dir(now: datetime | None = None) -> Path:
    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    day = now.day
    suffix = "th" if 10 <= day % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(day % 10, "th")
    return REPORTS_ROOT / f"{now:%Y}" / now.strftime("%B") / f"{now:%B} {day}{suffix}, {now:%Y}"


def _publish_path():
    from apps.core import config

    path = config.STATE_DIR / PUBLISH_NAME
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _publish_fingerprint(notice_id: str, hvo_text: str, alert_level: str) -> str:
    """Stable public-post key: HVO notice id + alert level. Ignore USGS lists and HVO clock text."""
    nid = (notice_id or "").strip()
    level = (alert_level or "").strip().lower()
    core = f"{nid}\n{level}\n"
    return hashlib.md5(core.encode("utf-8", errors="replace")).hexdigest()


def _load_publish() -> dict:
    path = _publish_path()
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def remember_publish(fp: str, notice_id: str, alert_level: str) -> None:
    payload = {
        "hash": fp,
        "notice_id": notice_id,
        "alert_level": alert_level,
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }
    path = _publish_path()
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def decide_publish(notice_id: str, hvo_text: str, alert_level: str) -> tuple[bool, str, str]:
    """Return (should_publish, fingerprint, reason). Seed the first seen notice without posting."""
    fp = _publish_fingerprint(notice_id, hvo_text, alert_level)
    prev = str((_load_publish() or {}).get("hash") or "")
    nid = (notice_id or "").strip()
    if not nid:
        return False, fp, "no-notice"
    if not prev:
        return False, fp, "seed"
    if prev == fp:
        return False, fp, "unchanged"
    return True, fp, "changed"


async def _fetch_hvo(client: httpx.AsyncClient) -> tuple[str, str]:
    r = await client.get(HANS_LIST)
    r.raise_for_status()
    ids = re.findall(r"DOI-USGS-HVO-[\dT:+\-]+", r.text)
    if not ids:
        return "", ""
    notice_id = ids[0]
    r2 = await client.get(HANS_NOTICE.format(id=notice_id))
    r2.raise_for_status()
    text = re.sub(r"<script[^>]*>.*?</script>", " ", r2.text, flags=re.S | re.I)
    text = re.sub(r"<style[^>]*>.*?</style>", " ", text, flags=re.S | re.I)
    text = re.sub(r"<[^>]+>", "\n", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    m = re.search(r"(KĪLAUEA|Kilauea).*", text, re.S)
    if m:
        text = m.group(0)[:4000]
    return notice_id, text


async def run():
    import asyncio

    log.info("Kilauea cron running  %s", datetime.now(timezone.utc).isoformat())
    try:
        async with httpx.AsyncClient(timeout=20, headers=HVO_UA) as client:
            r = await client.get(USGS_QUAKE_URL)
            if r.status_code != 200:
                log.warning("USGS quake fetch failed: %s", r.status_code)
                return
            features = r.json().get("features", [])
            notice_id, hvo_text = "", ""
            try:
                notice_id, hvo_text = await _fetch_hvo(client)
            except Exception as e:
                log.warning("HVO fetch failed: %s", e)

            from apps.core import config

            alert_level = _infer_alert_level(features, hvo_text)
            _write_alert_state(config, alert_level, hvo_text, features)
            should, fp, reason = decide_publish(notice_id, hvo_text, alert_level)
            if not should:
                if reason != "no-notice":
                    remember_publish(fp, notice_id, alert_level)
                log.info("Kīlauea: skip public post (%s)", reason)
                return

            from apps.core.services import reports, synth

            lines = [
                f"# Kīlauea\n",
                f"HVO notice: {notice_id or 'none'}\n",
                f"USGS events M≥1 ≤150km: {len(features)}\n",
            ]
            for f in features[:5]:
                props = f.get("properties") or {}
                lines.append(
                    f"- M{props.get('mag', '?')} {props.get('place', '?')} — {props.get('type', '?')}"
                )
            if hvo_text:
                lines.append("\n## HVO excerpt\n")
                lines.append(hvo_text[:1800])
            factual = "\n".join(lines)

            # Notice-only Grok → text + WAV + blog when toggles/spend allow.
            # Run sync generate off the event loop so /health and public chat stay alive.
            notice_out: dict = {}
            try:
                from apps.core.services import report_generation

                # Inject HVO text into a temp facts note for the package.
                notice_facts = config.STATE_DIR / "kilauea-notice-facts.txt"
                notice_facts.write_text(factual[:6000], encoding="utf-8")
                notice_out = await asyncio.to_thread(
                    report_generation.generate,
                    "kilauea",
                    dry_run=False,
                    allow_tts=True,
                    publish=True,
                    update_board=False,
                    play_after=False,
                )
            except Exception as e:
                log.warning("Kilauea notice generate failed: %s", e)
                notice_out = {"ok": False, "detail": type(e).__name__}

            system = (
                "You are Ava Ivy. Write a short public Kīlauea status for Discord. "
                "Use only the source text. No invented alert levels or numbers. "
                "Cover: alert/aviation if stated, erupting or paused, last episode if given, "
                "one hazard note. Under 220 words. No vendor names."
            )
            user = factual[:4500]
            content = None
            if notice_out.get("ok") and notice_out.get("text"):
                content = str(notice_out["text"])
            else:
                content = await asyncio.to_thread(
                    synth.polish,
                    "kilauea",
                    system,
                    user,
                    factual=factual[:1900],
                    channel="kilauea",
                )
            body = (content or "").strip() or factual
            if not body.strip():
                log.warning("Kīlauea: skip empty report")
                return

            report_path = _daily_report_dir() / f"kilauea-{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H')}.md"
            report_path.parent.mkdir(parents=True, exist_ok=True)
            report_path.write_text(body, encoding="utf-8")
            log.info("Kīlauea report written: %s", report_path.name)
            reports.queue_public_draft("kilauea", body[:1900], source="cron")
            remember_publish(fp, notice_id, alert_level)
            log.info("Kīlauea draft queued for operator review")

    except Exception:
        log.exception("Kīlauea cron failed")


def _infer_alert_level(features: list, hvo_text: str) -> str:
    """Official USGS/HVO levels only. Do not treat the word 'eruption' in
    'eruption is paused' / 'not erupting' as a live eruption."""
    blob = hvo_text or ""
    low = blob.lower()

    m = re.search(r"current volcano alert level:\s*(warning|watch|advisory|normal)", low)
    if m:
        return m.group(1)

    m = re.search(r"current aviation color code:\s*(red|orange|yellow|green)", low)
    if m:
        return {
            "red": "warning",
            "orange": "watch",
            "yellow": "advisory",
            "green": "normal",
        }[m.group(1)]

    paused = bool(
        re.search(r"not erupting|is paused|eruption (is )?paused|currently paused", low)
    )
    if paused:
        return "advisory" if ("advisory" in low or "yellow" in low) else "normal"

    if re.search(r"\bis erupting\b", low) or "aviation color code red" in low:
        return "warning"
    if "watch" in low or "orange" in low:
        return "watch"
    if "advisory" in low or "yellow" in low:
        return "advisory"
    return "normal"


def _headline(hvo_text: str, alert_level: str) -> str:
    low = (hvo_text or "").lower()
    if re.search(r"not erupting|is paused|eruption (is )?paused", low):
        return "not erupting — Halemaʻumaʻu paused"
    if alert_level == "warning":
        return "WARNING — check HVO daily update"
    if alert_level == "watch":
        return "WATCH — elevated unrest"
    if alert_level == "advisory":
        return "ADVISORY — unrest, not a live fountain"
    return "quiet"


def _write_alert_state(
    config, alert_level: str, hvo_text: str = "", features: list | None = None
) -> None:
    try:
        state_dir = config.STATE_DIR
        state_dir.mkdir(parents=True, exist_ok=True)
        state_path = state_dir / "kilauea-alert.json"
        erupting = alert_level in {"warning"} or bool(
            re.search(r"\bis erupting\b", (hvo_text or "").lower())
            and not re.search(r"not erupting|is paused", (hvo_text or "").lower())
        )
        now = datetime.now(timezone.utc).isoformat()
        headline = _headline(hvo_text, alert_level)
        alert = {
            "alert_level": alert_level,
            "multiplier": get_multiplier(alert_level),
            "erupting": erupting,
            "headline": headline,
            "updated_at": now,
        }
        state_path.write_text(json.dumps(alert, ensure_ascii=False) + "\n", encoding="utf-8")

        (state_dir / "kilauea-situation.json").write_text(
            json.dumps(
                {
                    "situation": {
                        "id": "current",
                        "name": headline[:80],
                        "enabled": alert_level not in {"", "normal"} or erupting,
                        "body": (hvo_text or headline)[:2000],
                        "updated_at": now,
                    }
                },
                ensure_ascii=False,
            )
            + "\n",
            encoding="utf-8",
        )
        from apps.core.services.kilauea_cams import DEFAULT_CAMS

        streams = []
        for cam in DEFAULT_CAMS:
            vid = cam.get("youtube_video_id") or ""
            streams.append(
                {
                    "id": cam["id"],
                    "title": cam.get("title") or cam["id"],
                    "description": "USGS official cam",
                    "youtube_video_id": vid,
                    "watch_url": f"https://www.youtube.com/watch?v={vid}",
                    "embed_url": (
                        f"https://www.youtube.com/embed/{vid}"
                        "?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                    ),
                }
            )
        (state_dir / "kilauea-live-streams.json").write_text(
            json.dumps(
                {"streams": streams, "updated_at": now, "source": "cron"},
                ensure_ascii=False,
            )
            + "\n",
            encoding="utf-8",
        )
        quakes = []
        for f in (features or [])[:20]:
            props = f.get("properties") or {}
            geom = f.get("geometry") or {}
            coords = geom.get("coordinates") or [None, None, None]
            quakes.append(
                {
                    "id": f.get("id"),
                    "mag": props.get("mag"),
                    "place": props.get("place"),
                    "time": props.get("time"),
                    "lon": coords[0],
                    "lat": coords[1],
                    "depth_km": coords[2],
                }
            )
        (state_dir / "kilauea-quakes.json").write_text(
            json.dumps(
                {"quakes": quakes, "updated_at": now, "source": "usgs"},
                ensure_ascii=False,
            )
            + "\n",
            encoding="utf-8",
        )
        (state_dir / "kilauea-dashboard.json").write_text(
            json.dumps(
                {
                    "ok": True,
                    "kilauea": alert,
                    "quakes_n": len(quakes),
                    "updated_at": now,
                    "source": "cron",
                },
                ensure_ascii=False,
            )
            + "\n",
            encoding="utf-8",
        )
        log.info("Kīlauea alert state written: %s erupting=%s", alert_level, erupting)
    except Exception as e:
        log.warning("Could not write kilauea alert state: %s", e)


def get_multiplier(alert_level: str) -> float:
    level = alert_level.lower().strip()
    if "erupt" in level or "red" in level or "warning" in level:
        return MULTIPLIERS["eruption"]
    if "watch" in level or "orange" in level:
        return MULTIPLIERS["watch"]
    if "advisory" in level or "yellow" in level:
        return MULTIPLIERS["advisory"]
    return MULTIPLIERS["normal"]
