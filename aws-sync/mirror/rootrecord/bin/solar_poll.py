#!/usr/bin/env python3
"""Pull solar stills via desk tunnel every 10m (sunrise→sundown only).

Desk: Cloudflare → origin mux → cam_gateway (ffmpeg + River car power).
AWS work/ holds Current.jpg / Current.gif (wiped by packer).
Durable automation KB: etc/solar-cam-kb.json (never wiped).
"""
from __future__ import annotations

import logging
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import WORK, ensure_dirs, load_dotenv, now_hst, sleep_until, write_json
import automation_kb as solar_kb

log = logging.getLogger("rr.solar")
INTERVAL_S = float(os.environ.get("RR_SOLAR_INTERVAL_S") or 600.0)
SETTLE_S = float(os.environ.get("RR_SOLAR_SETTLE_S") or 10.0)
# Cap night sleep so sun refresh / clock drift stay honest (default 30m)
NIGHT_SLEEP_CAP_S = float(os.environ.get("RR_SOLAR_NIGHT_SLEEP_CAP_S") or 1800.0)
DEST = WORK / "solar-cam"


def _base() -> str:
    return (os.environ.get("RR_SOLAR_BASE_URL") or "").rstrip("/")


def _client() -> httpx.Client:
    return httpx.Client(timeout=httpx.Timeout(60.0, connect=15.0), follow_redirects=True)


def _power(client: httpx.Client, base: str, on: bool) -> dict:
    path = "/power-on" if on else "/power-off"
    try:
        r = client.post(base + path)
        try:
            body = r.json()
        except Exception:
            body = {"raw": r.text[:200]}
        return {"ok": r.status_code < 400, "status": r.status_code, "body": body}
    except Exception as exc:
        return {"ok": False, "error": str(exc)[:200]}


def _fetch_still(client: httpx.Client, base: str) -> tuple[bytes | None, dict]:
    try:
        r = client.get(base + "/current.jpg")
        if r.status_code == 200 and r.content[:3] == b"\xff\xd8\xff":
            meta = {
                "ok": True,
                "bytes": len(r.content),
                "lit": r.headers.get("x-solar-cam-lit"),
                "status": r.status_code,
            }
            return r.content, meta
        return None, {"ok": False, "status": r.status_code, "detail": r.text[:200]}
    except Exception as exc:
        return None, {"ok": False, "error": str(exc)[:200]}


def _hour_dir(hst) -> Path:
    return DEST / "frames" / hst.strftime("%Y%m%d%H")


def _prune_old_hours(keep: Path) -> None:
    root = DEST / "frames"
    if not root.is_dir():
        return
    for child in root.iterdir():
        if child.is_dir() and child.resolve() != keep.resolve():
            shutil.rmtree(child, ignore_errors=True)


def _build_gif(hour: Path) -> Path | None:
    jpgs = sorted(hour.glob("*.jpg"))
    if not jpgs:
        return None
    out = DEST / "Current.gif"
    cmd = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-framerate", "1",
        "-pattern_type", "glob",
        "-i", str(hour / "*.jpg"),
        "-vf", "scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
        "-loop", "0",
        str(out),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=120)
        if out.is_file() and out.stat().st_size > 0:
            return out
    except Exception as exc:
        log.warning("gif glob failed: %s — trying concat", exc)
    lst = hour / "_list.txt"
    lst.write_text("".join(f"file '{p.name}'\nduration 1\n" for p in jpgs) + f"file '{jpgs[-1].name}'\n", encoding="utf-8")
    cmd2 = [
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
        "-f", "concat", "-safe", "0", "-i", str(lst),
        "-vf", "scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
        "-loop", "0",
        str(out),
    ]
    try:
        subprocess.run(cmd2, check=True, cwd=str(hour), timeout=120)
    except Exception as exc:
        log.warning("gif concat failed: %s", exc)
        return None
    finally:
        try:
            lst.unlink(missing_ok=True)
        except Exception:
            pass
    return out if out.is_file() and out.stat().st_size > 0 else None


def poll_once(*, force: bool = False) -> dict:
    load_dotenv()
    ensure_dirs()
    DEST.mkdir(parents=True, exist_ok=True)
    (DEST / "frames").mkdir(parents=True, exist_ok=True)

    sun = solar_kb.refresh_sun()
    win = solar_kb.daylight_window(sun)
    if not force and not win["daylight_now"]:
        out = {
            "ok": True,
            "skipped": True,
            "skip_reason": win.get("skip_reason"),
            "sunrise": win.get("sunrise"),
            "sunset": win.get("sunset"),
            "updated_at": now_hst().isoformat(),
        }
        write_json(DEST / "Current.meta.json", out)
        solar_kb.update_solar_cam(out, skipped=True, skip_reason=str(win.get("skip_reason") or "night"))
        return out

    base = _base()
    if not base:
        meta = {"ok": False, "error": "RR_SOLAR_BASE_URL unset", "updated_at": now_hst().isoformat()}
        write_json(DEST / "Current.meta.json", meta)
        solar_kb.update_solar_cam(meta)
        return meta

    with _client() as client:
        data, meta = _fetch_still(client, base)
        if data is None:
            pon = _power(client, base, True)
            we_powered = bool((pon.get("body") or {}).get("we_powered")) or bool(pon.get("ok"))
            time.sleep(SETTLE_S)
            data, meta = _fetch_still(client, base)
            meta["power_on"] = pon
            if we_powered or pon.get("ok"):
                poff = _power(client, base, False)
                meta["power_off"] = poff

    hst = now_hst()
    hour = _hour_dir(hst)
    hour.mkdir(parents=True, exist_ok=True)
    if data:
        frame = hour / f"frame-{hst.strftime('%M%S')}.jpg"
        frame.write_bytes(data)
        (DEST / "Current.jpg").write_bytes(data)
        gif = _build_gif(hour)
        _prune_old_hours(hour)
        out = {
            "ok": True,
            "bytes": len(data),
            "frame": str(frame.relative_to(DEST)),
            "gif": bool(gif),
            "gif_bytes": gif.stat().st_size if gif else 0,
            "lit": meta.get("lit"),
            "updated_at": hst.isoformat(),
            "power": {k: meta[k] for k in ("power_on", "power_off") if k in meta},
            "sunrise": win.get("sunrise"),
            "sunset": win.get("sunset"),
        }
    else:
        out = {
            "ok": False,
            "detail": meta,
            "updated_at": hst.isoformat(),
            "sunrise": win.get("sunrise"),
            "sunset": win.get("sunset"),
        }
    write_json(DEST / "Current.meta.json", out)
    solar_kb.update_solar_cam(out)
    return out


def main() -> None:
    load_dotenv()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    log.info("solar-cam poller interval=%ss window=sunrise→sunset kb=%s", INTERVAL_S, solar_kb.KB_PATH)
    while True:
        started = time.monotonic()
        try:
            meta = poll_once()
            if meta.get("skipped"):
                log.info(
                    "solar skip=%s sunrise=%s sunset=%s",
                    meta.get("skip_reason"),
                    meta.get("sunrise"),
                    meta.get("sunset"),
                )
                # Sleep toward sunrise (capped) so night is quiet
                delay = min(NIGHT_SLEEP_CAP_S, solar_kb.seconds_until_sunrise())
                time.sleep(max(60.0, delay))
                continue
            log.info(
                "solar ok=%s bytes=%s gif=%s lit=%s",
                meta.get("ok"),
                meta.get("bytes"),
                meta.get("gif"),
                meta.get("lit"),
            )
        except Exception as exc:
            log.warning("solar poll failed: %s", exc)
            write_json(
                DEST / "Current.meta.json",
                {"ok": False, "detail": str(exc)[:300], "updated_at": now_hst().isoformat()},
            )
            try:
                solar_kb.update_solar_cam({"ok": False, "error": str(exc)[:300]})
            except Exception:
                pass
        sleep_until(INTERVAL_S, started)


if __name__ == "__main__":
    main()
