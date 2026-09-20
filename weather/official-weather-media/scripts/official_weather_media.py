"""Official NHC/NWS graphics, HLS text, archive, OBS scenes, and audio."""

from __future__ import annotations

import hashlib
import json
import logging
import re
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import requests

from apps.core import config

log = logging.getLogger("ava.official_weather_media")
UA = "AvaIvy/2.0 official NHC NWS media"
ROOT = config.PUBLIC_MEDIA / "images" / "weather" / "official"
ARCHIVE = ROOT / "archive"
ZIP_PATH = ARCHIVE / "official_weather_archive.zip"
STATE_PATH = config.STATE_DIR / "official-weather-media.json"
HLS_URL = "https://www.weather.gov/hfo/HLS"
HLS_PRODUCTS = "https://api.weather.gov/products/types/HLS/locations/HFO"
HWO_PRODUCTS = "https://api.weather.gov/products/types/HWO/locations/HFO"
AFD_PRODUCTS = "https://api.weather.gov/products/types/AFD/locations/HFO"
HLS_TXT = "https://forecast.weather.gov/product.php?site=HFO&issuedby=HFO&product=HLS&format=txt&version=1&glossary=0"
HWO_TXT = "https://forecast.weather.gov/product.php?site=HFO&issuedby=HFO&product=HWO&format=txt&version=1&glossary=0"
JSON_HEADERS = {"User-Agent": UA, "Accept": "application/ld+json"}

ASSETS = {
    "nhc_cpac_7day": ("https://www.nhc.noaa.gov/gtwo.php?basin=cpac&fdays=7", "page"),
    "nhc_epac_7day": ("https://www.nhc.noaa.gov/gtwo.php?basin=epac&fdays=7", "page"),
    "nhc_atlc_7day": ("https://www.nhc.noaa.gov/gtwo.php?basin=atlc&fdays=7", "page"),
    "nhc_epac_2day": ("https://www.nhc.noaa.gov/gtwo.php?basin=epac&fdays=2", "page"),
    "nhc_cpac_2day": ("https://www.nhc.noaa.gov/gtwo.php?basin=cpac&fdays=2", "page"),
    "hfo_watch_warning_map": ("https://www.weather.gov/wwamap/png/hfo.png", "png"),
    "hawaii_ir_loop": ("https://www.weather.gov/images/hfo/satellite/Hawaii_IR_loop.gif", "gif"),
    "north_pacific_graphic": ("https://www.weather.gov/images/hfo/graphics/npac.gif", "gif"),
}

SCENES = tuple((f"Official · {slug}", slug) for slug in ASSETS)


def _current(slug: str, kind: str) -> Path:
    suffix = ".html" if kind == "page" else f".{kind}"
    path = ROOT / f"{slug}-current{suffix}"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _save_state(payload: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    STATE_PATH.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def _clean_hls(text: str) -> str:
    text = text.replace("&&", ". ").replace("$", "").replace("&", "and")
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n\n", text)
    return text.strip()[:14000] + "\n"


def _looks_like_product(text: str) -> bool:
    t = (text or "").strip()
    if len(t) < 80:
        return False
    head = t[:500].lower()
    if "<html" in head or "googleanalytics" in head or "xml_logo.gif" in head:
        return False
    return True


def _nws_latest_product(list_url: str, fallback_txt: str = "") -> str:
    try:
        response = requests.get(list_url, headers=JSON_HEADERS, timeout=45)
        response.raise_for_status()
        items = response.json().get("@graph") or []
        if items:
            pid = items[0].get("@id") or items[0].get("id") or ""
            if pid:
                body = requests.get(pid, headers=JSON_HEADERS, timeout=45)
                body.raise_for_status()
                text = str(body.json().get("productText") or "").strip()
                if _looks_like_product(text):
                    return _clean_hls(text)
    except Exception as exc:
        log.warning("NWS product list failed %s: %s", list_url, exc)
    if not fallback_txt:
        return ""
    try:
        response = requests.get(fallback_txt, headers={"User-Agent": UA}, timeout=45)
        response.raise_for_status()
        text = response.text
        pre = re.search(r"<pre[^>]*>(.*?)</pre>", text, re.I | re.S)
        if pre:
            text = pre.group(1)
        elif "<html" in text.lower():
            return ""
        if _looks_like_product(text):
            return _clean_hls(text)
        return ""
    except Exception as exc:
        log.warning("NWS product text failed %s: %s", fallback_txt, exc)
        return ""


def _official_statement() -> tuple[str, str]:
    hls = _nws_latest_product(HLS_PRODUCTS, HLS_TXT)
    hwo = _nws_latest_product(HWO_PRODUCTS, HWO_TXT)
    afd = _nws_latest_product(AFD_PRODUCTS, "")
    labeled = []
    if hls:
        labeled.append(hls)
    if hwo:
        labeled.append(hwo)
    if afd:
        labeled.append(afd)
    blob = ("\n\n".join(labeled) + "\n") if labeled else ""
    spoken = (hls or hwo or afd or "").strip()
    if not spoken:
        spoken = "Honolulu National Weather Service has no local hurricane statement in effect."
        blob = spoken + "\n"
    if len(spoken) > 4500:
        spoken = spoken[:4500].rsplit(" ", 1)[0] + "."
    return blob, spoken


def _hls_statement(html: str) -> str:
    match = re.search(r'["\']([^"\']*HLS[^"\']*\.xml)["\']', html, re.I)
    feed_url = "https://www.weather.gov" + match.group(1) if match and match.group(1).startswith("/") else (match.group(1) if match else "")
    if feed_url:
        try:
            feed = requests.get(feed_url, headers={"User-Agent": UA}, timeout=45)
            feed.raise_for_status()
            text = re.sub(r"<[^>]+>", " ", feed.text)
            return _clean_hls(text)
        except Exception as exc:
            log.warning("HLS XML fetch failed: %s", exc)
    text = re.sub(r"<script.*?</script>|<style.*?</style>", " ", html, flags=re.I | re.S)
    return _clean_hls(re.sub(r"<[^>]+>", " ", text))


def _archive(files: dict[str, bytes]) -> None:
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    entries: dict[str, bytes] = {}
    if ZIP_PATH.is_file():
        with zipfile.ZipFile(ZIP_PATH) as old:
            entries = {n: old.read(n) for n in old.namelist()}
    entries.update(files)
    temp = ZIP_PATH.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temp, "w", zipfile.ZIP_DEFLATED) as out:
        for name, data in sorted(entries.items()):
            out.writestr(name, data)
    temp.replace(ZIP_PATH)


def _write_audio(text: str) -> dict:
    dest = config.GENERATED_DIR / "NWS_Official_statement.wav"
    try:
        from apps.voice.speakers import speak_report

        result = speak_report("official", f"Official NWS Honolulu statement. {text}", dest)
        return {
            "ok": bool(result.get("ok")),
            "skipped": bool(result.get("skipped")),
            "engine": "kokoro",
            "path": str(dest),
            "detail": result.get("detail"),
        }
    except Exception as exc:
        return {"ok": False, "engine": "kokoro", "detail": str(exc)[:240]}


def run() -> dict:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    downloaded = {}
    current = {}
    errors = {}
    for slug, (url, kind) in ASSETS.items():
        try:
            response = requests.get(url, headers={"User-Agent": UA}, timeout=45)
            response.raise_for_status()
            data = response.content
            ext = "html" if kind == "page" else kind
            dated_name = f"{slug}-{stamp}.{ext}"
            ARCHIVE.mkdir(parents=True, exist_ok=True)
            _current(slug, kind).write_bytes(data)
            (ARCHIVE / dated_name).write_bytes(data)
            downloaded[slug] = {"url": url, "bytes": len(data), "current": str(_current(slug, kind))}
            current[slug] = str(_current(slug, kind))
        except Exception as exc:
            errors[slug] = str(exc)[:240]

    try:
        hls_text, spoken_text = _official_statement()
        hls_path = config.REPORTS_DIR / "NWS_Official_statement.txt"
        prior = hls_path.read_text(encoding="utf-8") if hls_path.is_file() else ""
        changed = hashlib.sha256(prior.encode()).hexdigest() != hashlib.sha256(hls_text.encode()).hexdigest()
        hls_path.parent.mkdir(parents=True, exist_ok=True)
        hls_path.write_text(hls_text, encoding="utf-8")
        dated_txt = config.REPORTS_DIR / f"NWS_Official_statement-{stamp}.txt"
        dated_txt.write_text(hls_text, encoding="utf-8")
        wav = config.GENERATED_DIR / "NWS_Official_statement.wav"
        need_audio = bool(spoken_text.strip()) and (changed or not wav.is_file() or wav.stat().st_size <= 0)
        audio = _write_audio(spoken_text) if need_audio else {"ok": True, "skipped": True, "detail": "unchanged"}
    except Exception as exc:
        hls_text = ""
        changed = False
        audio = {"ok": False, "detail": str(exc)[:240]}
        errors["hls"] = str(exc)[:240]

    archive_files = {}
    for slug, (url, kind) in ASSETS.items():
        path = _current(slug, kind)
        if path.is_file():
            archive_files[f"{slug}-{stamp}{path.suffix}"] = path.read_bytes()
    if archive_files:
        _archive(archive_files)
    payload = {
        "ok": not errors,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "downloaded": downloaded,
        "current": current,
        "archive_zip": str(ZIP_PATH),
        "hls": {"url": HLS_URL, "changed": changed, "audio": audio},
        "errors": errors,
    }
    _save_state(payload)
    return payload


async def apply_obs_scenes() -> dict:
    from apps.core.services.obs_presence import obs_work_allowed
    from apps.core.services.obs_studio import ObsClient, _ensure_input, _fit, save_rotation_config

    if not obs_work_allowed():
        return {"ok": True, "skipped": "obs_off"}
    obs = ObsClient()
    if not await obs.connect():
        return {"ok": False, "detail": "obs_unreachable"}
    created = []
    try:
        existing = {s.get("sceneName") for s in (await obs.req("GetSceneList")).get("scenes") or []}
        dwell = {}
        for scene, slug in SCENES:
            if scene not in existing:
                continue
            url, kind = ASSETS[slug]
            name = f"Official {slug}"
            current = _current(slug, kind)
            if kind == "page":
                source_url = f"http://127.0.0.1:8787/obs/official/{slug}" if current.is_file() else url
                await _ensure_input(obs, scene, name, "browser_source", {"url": source_url, "width": 1920, "height": 1080, "shutdown": True, "restart_when_active": True})
            else:
                await _ensure_input(obs, scene, name, "image_source", {"file": str(current), "unload": False})
            await _fit(obs, scene, name)
            await _ensure_input(
                obs,
                scene,
                "Ava Speaking Overlay",
                "browser_source",
                {
                    "url": "http://127.0.0.1:8787/obs/speaking-overlay",
                    "width": 1920,
                    "height": 1080,
                    "shutdown": False,
                    "restart_when_active": False,
                    "css": "body { margin: 0; overflow: hidden; background: transparent; }",
                },
            )
            await _fit(obs, scene, "Ava Speaking Overlay")
            created.append(scene)
            dwell[scene] = 10
        save_rotation_config(mode_dwell_s={"official": 10}, scene_dwell_s=dwell)
        return {"ok": True, "scenes": created, "dwell_s": 10}
    finally:
        await obs.close()
