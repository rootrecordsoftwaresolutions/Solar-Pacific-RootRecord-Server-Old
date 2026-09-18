#!/usr/bin/env python3
"""Rear Shed panels camera on Night Owl ch1 + River 2 Pro car 12V.

Flow: ensure car DC on → wait for a lit RTSP frame → save JPEG → turn car
DC off unless drives are holding it. Never toggles AC. Never invents watts.
"""
from __future__ import annotations

import json
import logging
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

log = logging.getLogger("ava.panels_cam")

SKILL = Path(__file__).resolve().parents[1]
STORE = SKILL / "store"
FRAMES = STORE / "frames"
CONN_PATH = STORE / "CONNECTION.json"
# Keep a copy under look for the earlier discovery notes
LOOK_CONN = (
    Path.home() / ".ollama" / "skills" / "look" / "store" / "camera-dvr" / "CONNECTION.json"
)
OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
RIVER_SCRIPTS = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
STATE_NAME = "panels-cam.json"
PHOTO_MARKER = "PANELS_PHOTO="

DEFAULT_STATE: dict[str, Any] = {
    "purpose": "panels-cam",
    "auto": True,
    "channel": 1,
    "stream": 0,
    "settle_s": 8,
    "wait_s": 50,
    "last_tick": None,
    "last_ok": None,
    "last_path": None,
    "last_skip": None,
    "note": "Night Owl ch1 Rear Shed. Powered from River car 12V. Off after grab unless drives hold car.",
}


def _ensure_paths() -> None:
    FRAMES.mkdir(parents=True, exist_ok=True)
    (OPS / "state").mkdir(parents=True, exist_ok=True)


def state_path() -> Path:
    return OPS / "state" / STATE_NAME


def load_state() -> dict[str, Any]:
    base = dict(DEFAULT_STATE)
    path = state_path()
    if not path.is_file():
        return base
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return base
    if isinstance(raw, dict):
        base.update(raw)
    base["auto"] = bool(base.get("auto", True))
    return base


def save_state(st: dict[str, Any]) -> dict[str, Any]:
    _ensure_paths()
    path = state_path()
    out = dict(DEFAULT_STATE)
    out.update(st)
    out["auto"] = bool(out.get("auto", True))
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)
    return out


def load_conn() -> dict[str, Any]:
    for path in (CONN_PATH, LOOK_CONN):
        if path.is_file():
            try:
                raw = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    return raw
            except Exception:
                continue
    return {}


def rtsp_url(*, channel: int = 1, stream: int = 0) -> str:
    conn = load_conn()
    lan = conn.get("lan") if isinstance(conn.get("lan"), dict) else {}
    ip = str(lan.get("ip") or "192.168.1.33").strip()
    user = str(lan.get("rtsp_user") or "admin").strip() or "admin"
    password = str(lan.get("rtsp_password") or "admin").strip() or "admin"
    return (
        f"rtsp://{user}:{password}@{ip}:554/"
        f"user={user}&password={password}&channel={int(channel)}&stream={int(stream)}"
    )


def _import_river():
    ble_scripts = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
    for p in (RIVER_SCRIPTS, ble_scripts):
        s = str(p)
        if s not in sys.path:
            sys.path.insert(0, s)
    import river_car_dc as river  # noqa: WPS433

    return river


def drives_hold_car() -> bool:
    """True if external-drive automation wants car DC left on."""
    river = _import_river()
    car = river.load_state()
    if bool(car.get("wanted")):
        return True
    da = OPS / "state" / "drive-automation.json"
    if not da.is_file():
        return False
    try:
        raw = json.loads(da.read_text(encoding="utf-8"))
    except Exception:
        return False
    return bool(isinstance(raw, dict) and (raw.get("hold_car_on") or raw.get("wanted")))


def set_car_power(*, want_on: bool, execute: bool) -> dict[str, Any]:
    river = _import_river()
    return river.set_car(want_on=bool(want_on), execute=bool(execute))


def frame_stats(path: Path) -> dict[str, float]:
    try:
        from PIL import Image
    except ImportError:
        return {"mean": -1.0, "lit_pct": -1.0}
    im = Image.open(path).convert("L")
    pix = list(im.get_flattened_data()) if hasattr(im, "get_flattened_data") else list(im.getdata())
    if not pix:
        return {"mean": 0.0, "lit_pct": 0.0}
    mean = sum(pix) / len(pix)
    lit = sum(1 for v in pix if v > 16)
    return {"mean": float(mean), "lit_pct": 100.0 * lit / len(pix), "w": float(im.size[0]), "h": float(im.size[1])}


def is_lit(stats: dict[str, float]) -> bool:
    # OSD-only black frames were ~0.7–2.6% lit / mean < 3
    return float(stats.get("mean") or 0) >= 12.0 or float(stats.get("lit_pct") or 0) >= 8.0


def grab_jpeg(*, channel: int, stream: int, out: Path) -> dict[str, Any]:
    url = rtsp_url(channel=channel, stream=stream)
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-rtsp_transport",
        "tcp",
        "-i",
        url,
        "-frames:v",
        "1",
        "-update",
        "1",
        "-q:v",
        "2",
        str(out),
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=25, check=False)
    except (OSError, subprocess.TimeoutExpired) as e:
        return {"ok": False, "error": str(e), "path": str(out)}
    if proc.returncode != 0 or not out.is_file() or out.stat().st_size < 500:
        err = (proc.stderr or b"").decode("utf-8", "replace")[:200]
        return {"ok": False, "error": err or f"exit {proc.returncode}", "path": str(out)}
    stats = frame_stats(out)
    return {"ok": True, "path": str(out), "lit": is_lit(stats), "stats": stats}


def ensure_live_frame(
    *,
    execute: bool = True,
    channel: int | None = None,
    stream: int | None = None,
    power: bool = True,
) -> dict[str, Any]:
    """Power on if needed, grab until lit or timeout. Returns report + path.

    If River car 12V was already on, leave it on after (manual always-on).
    Only force-off when this call turned it on from off.
    """
    _ensure_paths()
    st = load_state()
    ch = int(channel if channel is not None else st.get("channel") or 1)
    sm = int(stream if stream is not None else st.get("stream") or 0)
    settle = int(st.get("settle_s") or 8)
    wait_s = int(st.get("wait_s") or 50)
    river = _import_river()
    was_on = river.car_already_on() is True
    report: dict[str, Any] = {
        "ok": False,
        "channel": ch,
        "stream": sm,
        "execute": bool(execute),
        "powered": False,
        "left_on": False,
        "was_already_on": was_on,
    }

    # Quick try first (camera may already be powered)
    ts = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
    out = FRAMES / f"ch{ch}-{ts}.jpg"
    first = grab_jpeg(channel=ch, stream=sm, out=out)
    report["first"] = {k: first.get(k) for k in ("ok", "lit", "stats", "error")}
    if first.get("ok") and first.get("lit"):
        report["ok"] = True
        report["path"] = first["path"]
        report["skipped_power"] = True
        report["left_on"] = was_on
        st["last_ok"] = time.time()
        st["last_path"] = first["path"]
        st["last_skip"] = None
        save_state(st)
        return report

    if not power:
        report["error"] = "camera_dark_no_power"
        st["last_skip"] = "camera_dark"
        save_state(st)
        return report

    car_on = set_car_power(want_on=True, execute=bool(execute))
    report["car_on"] = {k: car_on.get(k) for k in ("ok", "action", "wanted", "execute", "verify_car_on", "put")}
    if execute and not car_on.get("ok"):
        report["error"] = "car_power_failed"
        st["last_skip"] = "car_power_failed"
        save_state(st)
        return report
    we_powered = bool(execute) and not was_on
    report["powered"] = we_powered
    if not execute:
        report["blocked"] = "dry_run"
        st["last_skip"] = "dry_run"
        save_state(st)
        return report

    time.sleep(max(2, settle))
    deadline = time.time() + max(5, wait_s)
    last: dict[str, Any] = {}
    while time.time() < deadline:
        ts = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
        out = FRAMES / f"ch{ch}-{ts}.jpg"
        last = grab_jpeg(channel=ch, stream=sm, out=out)
        if last.get("ok") and last.get("lit"):
            report["ok"] = True
            report["path"] = last["path"]
            report["stats"] = last.get("stats")
            break
        time.sleep(3)

    if not report.get("ok"):
        report["error"] = "no_lit_frame"
        report["last_grab"] = {k: last.get(k) for k in ("ok", "lit", "stats", "error", "path")}
        st["last_skip"] = "no_lit_frame"
        save_state(st)
    else:
        st["last_ok"] = time.time()
        st["last_path"] = report["path"]
        st["last_skip"] = None
        save_state(st)

    # Leave on if operator already had car DC up, or drives hold it.
    if we_powered:
        if was_on or drives_hold_car():
            report["left_on"] = True
            report["car_off"] = {"ok": True, "skipped": "already_on" if was_on else "drives_hold"}
        else:
            car_off = river.set_car(want_on=False, execute=True, force=True)
            report["car_off"] = {
                k: car_off.get(k) for k in ("ok", "action", "verify_car_on", "put", "left_on")
            }
            report["left_on"] = bool(car_off.get("left_on"))
    else:
        report["left_on"] = was_on
    return report


def latest_frame() -> Path | None:
    """Most recent saved still — no power cycle."""
    _ensure_paths()
    st = load_state()
    cand = st.get("last_path")
    if cand:
        p = Path(str(cand))
        if p.is_file() and p.stat().st_size > 500:
            return p
    jpgs = sorted(FRAMES.glob("ch*.jpg"), key=lambda x: x.stat().st_mtime, reverse=True)
    return jpgs[0] if jpgs else None


def show(*, execute: bool = True) -> dict[str, Any]:
    """Owner/council path: get a lit still and print PANELS_PHOTO= for Telegram."""
    return ensure_live_frame(execute=execute, power=True)


def cycle(*, execute: bool = True) -> dict[str, Any]:
    """15-min automation: on → grab → save → off only if we powered from off."""
    return ensure_live_frame(execute=execute, power=True)


def tick(*, execute: bool = True) -> dict[str, Any]:
    st = load_state()
    st["last_tick"] = time.time()
    if not st.get("auto", True):
        st["last_skip"] = "auto_off"
        save_state(st)
        return {"ok": True, "skipped": "auto_off"}
    report = cycle(execute=bool(execute))
    st = load_state()
    st["last_tick"] = time.time()
    if report.get("ok"):
        st["last_skip"] = None
    elif not st.get("last_skip"):
        st["last_skip"] = report.get("error") or "failed"
    save_state(st)
    report["auto"] = True
    return report


async def run() -> None:
    report = tick(execute=True)
    log.info(
        "panels-cam ok=%s path=%s skip=%s",
        report.get("ok"),
        report.get("path"),
        report.get("skipped") or report.get("error") or report.get("last_skip"),
    )


def status() -> dict[str, Any]:
    st = load_state()
    latest = None
    if FRAMES.is_dir():
        jpgs = sorted(FRAMES.glob("ch*.jpg"), key=lambda p: p.stat().st_mtime, reverse=True)
        if jpgs:
            latest = str(jpgs[0])
    return {
        "ok": True,
        "state": st,
        "latest_frame": latest or st.get("last_path"),
        "drives_hold_car": drives_hold_car(),
        "conn": bool(load_conn()),
    }


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Panels cam (Night Owl ch1 + River car DC).")
    parser.add_argument("--show", action="store_true", help="Power if needed, grab, print PANELS_PHOTO=.")
    parser.add_argument("--cycle", action="store_true", help="Same as show (scheduler-friendly).")
    parser.add_argument("--tick", action="store_true", help="Scheduler tick (respects auto).")
    parser.add_argument("--status", action="store_true")
    parser.add_argument("--auto", choices=("on", "off"))
    parser.add_argument("--execute", action="store_true", help="PUT car DC + real grab.")
    parser.add_argument("--dry-run", action="store_true", help="No PUT (default without --execute).")
    args = parser.parse_args(argv)

    execute = bool(args.execute) and not bool(args.dry_run)
    # CLI convenience: --show/--cycle/--tick imply execute unless --dry-run
    if (args.show or args.cycle or args.tick) and not args.dry_run:
        execute = True

    if args.auto:
        st = load_state()
        st["auto"] = args.auto == "on"
        print(json.dumps(save_state(st), indent=2))
        return 0
    if args.status or not (args.show or args.cycle or args.tick):
        print(json.dumps(status(), indent=2, default=str))
        return 0
    if args.tick:
        report = tick(execute=execute)
    else:
        report = show(execute=execute)

    # Human/council-facing summary (no password)
    if report.get("ok") and report.get("path"):
        print(f"ok Rear Shed panels")
        print(f"{PHOTO_MARKER}{report['path']}")
        print(json.dumps({k: report.get(k) for k in ("ok", "powered", "left_on", "stats", "skipped_power")}, indent=2))
        return 0
    print(json.dumps(report, indent=2, default=str))
    return 1 if not report.get("ok") and not report.get("skipped") else 0


if __name__ == "__main__":
    raise SystemExit(main())
