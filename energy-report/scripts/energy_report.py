#!/usr/bin/env python3
"""Carly energy report — latest Rear Shed still + pack facts + vision caption.

Does not cycle River car DC. Uses the most recent saved panels frame.
"""
from __future__ import annotations

import json
import logging
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

log = logging.getLogger("ava.energy_report")
HST = ZoneInfo("Pacific/Honolulu")
SKILL = Path(__file__).resolve().parents[1]
STORE = SKILL / "store"
STATE_NAME = "energy-report.json"
OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"


def state_path() -> Path:
    return OPS / "state" / STATE_NAME


def load_state() -> dict[str, Any]:
    base = {
        "auto": True,
        "last_ok": None,
        "last_skip": None,
        "last_path": None,
        "last_caption": None,
    }
    p = state_path()
    if not p.is_file():
        return base
    try:
        raw = json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return base
    if isinstance(raw, dict):
        base.update(raw)
    return base


def save_state(st: dict[str, Any]) -> None:
    p = state_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(st, indent=2) + "\n", encoding="utf-8")
    tmp.replace(p)


def latest_panels_frame() -> Path | None:
    root = Path.home() / ".ollama" / "skills" / "panels-cam" / "scripts"
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    from panels_grab import latest_frame

    return latest_frame()


def _pack_line(label: str, sn: str) -> str | None:
    ble = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
    if str(ble) not in sys.path:
        sys.path.insert(0, str(ble))
    from ecoflow_ble_store import (  # noqa: WPS433
        quota_age_s,
        quota_data,
        quota_path,
        quota_source,
        pv_w,
    )

    path = quota_path(sn)
    if not path.is_file():
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    data = quota_data(raw)
    age = quota_age_s(raw)
    if age is None or age > 600:
        return f"{label}: no fresh reading"
    soc = data.get("bmsMaster.soc") or data.get("bms_bmsStatus.soc") or data.get("soc")
    try:
        soc_n = float(soc) if soc is not None else None
    except (TypeError, ValueError):
        soc_n = None
    solar = pv_w(data)
    # Prefer measured output if present
    out_w = None
    for key in ("pd.wattsOutSum", "inv.outputWatts", "mppt.outWatts"):
        if data.get(key) is not None:
            try:
                out_w = float(data[key])
                break
            except (TypeError, ValueError):
                pass
    bits = [label]
    if soc_n is not None:
        bits.append(f"{soc_n:.0f}%")
    if solar is not None:
        bits.append(f"PV {solar:.0f} W")
    if out_w is not None:
        bits.append(f"out {out_w:.0f} W")
    bits.append(f"({quota_source(raw)}, {int(age)}s)")
    return " · ".join(bits)


def pack_facts() -> list[str]:
    ble = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
    if str(ble) not in sys.path:
        sys.path.insert(0, str(ble))
    from ecoflow_ble_store import DELTA_SN, RIVER_SN

    lines = []
    for label, sn in (("Delta 2", DELTA_SN), ("River 2 Pro", RIVER_SN)):
        line = _pack_line(label, sn)
        if line:
            lines.append(line)
    return lines


def weather_line() -> str | None:
    try:
        from apps.core.services.reports import latest_report

        nws = latest_report("nws-weather-*.md") or latest_report("nws-hawaii-*.md")
    except Exception:
        nws = None
    if not nws or not Path(nws).is_file():
        # hybrid / nws-hawaii skill store
        for cand in (
            Path.home() / ".ollama" / "skills" / "nws-hawaii" / "store",
            Path.home() / ".ollama" / "skills" / "reports" / "store",
        ):
            if cand.is_dir():
                hits = sorted(cand.rglob("nws*.md"), key=lambda p: p.stat().st_mtime, reverse=True)
                if hits:
                    nws = hits[0]
                    break
    if not nws:
        return None
    path = Path(nws)
    age_m = int((time.time() - path.stat().st_mtime) / 60)
    text = path.read_text(encoding="utf-8", errors="replace")
    snippet = ""
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith("#") or s.startswith("---"):
            continue
        snippet = s
        break
    if not snippet:
        return f"Weather: NWS file {age_m}m old"
    return f"Weather ({age_m}m): {snippet[:220]}"


def _vision_still(path: Path) -> Path:
    """Downscale for look model context limits (2K stills blow 2048 ctx)."""
    try:
        from PIL import Image
    except ImportError:
        return path
    out = STORE / "vision-thumb.jpg"
    STORE.mkdir(parents=True, exist_ok=True)
    im = Image.open(path).convert("RGB")
    im.thumbnail((960, 720))
    im.save(out, format="JPEG", quality=82, optimize=True)
    return out if out.is_file() and out.stat().st_size > 400 else path


def caption_still(path: Path) -> str:
    """Vision caption via look model. Facts only."""
    try:
        from apps.core.services import ollama as ollama_svc
    except Exception:
        try:
            sys.path.insert(0, str(Path.home() / ".ollama" / "skills" / "ollama-client" / "scripts"))
            import ollama as ollama_svc  # type: ignore
        except Exception as e:
            return f"(vision offline: {type(e).__name__})"
    prompt = (
        "This is a live still from the Rear Shed security camera looking at solar panels "
        "on site in Hawaiʻi. Describe only what is visible in three short factual sentences: "
        "sky/weather look, panel/ground condition, anything that affects solar (shade, rain, "
        "clouds, debris). If it is dark or blank, say that. Do not invent numbers or pack SOC."
    )
    thumb = _vision_still(path)
    try:
        cap = ollama_svc.look_sync(prompt, [thumb], timeout=120)
    except Exception as e:
        return f"(vision miss: {type(e).__name__})"
    return (cap or "").strip() or "(no caption)"


def build_transcript(*, caption: str, packs: list[str], weather: str | None, frame: Path | None) -> str:
    now = datetime.now(HST)
    lines = [
        f"Energy desk — {now.strftime('%Y-%m-%d %H:%M')} HST",
        "",
        "Rear Shed security cam (latest still, no power cycle).",
    ]
    if frame:
        age_m = int((time.time() - frame.stat().st_mtime) / 60)
        lines.append(f"Still age: {age_m} minute(s).")
    lines.append("")
    lines.append("Cam read:")
    lines.append(caption)
    lines.append("")
    if packs:
        lines.append("Packs:")
        lines.extend(f"- {p}" for p in packs)
        lines.append("")
    if weather:
        lines.append(weather)
        lines.append("")
    lines.append("Carly — site security + energy watch. Reply with notes if this should be better.")
    return "\n".join(lines).strip()


def build_report(*, post: bool = True, vision: bool = True) -> dict[str, Any]:
    STORE.mkdir(parents=True, exist_ok=True)
    st = load_state()
    frame = latest_panels_frame()
    packs = pack_facts()
    weather = weather_line()
    caption = (
        caption_still(frame)
        if (vision and frame)
        else ("(vision skipped)" if frame else "(no still on disk yet — run panels grab first)")
    )
    body = build_transcript(caption=caption, packs=packs, weather=weather, frame=frame)

    out_md = STORE / f"energy-{datetime.now(HST).strftime('%Y%m%d-%H%M')}.md"
    out_md.write_text(body + "\n", encoding="utf-8")
    # stable pointer for hybrid / solar attach
    latest_md = STORE / "energy-latest.md"
    latest_md.write_text(body + "\n", encoding="utf-8")
    if frame:
        pointer = STORE / "LATEST_FRAME.txt"
        pointer.write_text(str(frame) + "\n", encoding="utf-8")

    report: dict[str, Any] = {
        "ok": True,
        "path": str(out_md),
        "frame": str(frame) if frame else None,
        "caption": caption,
        "packs": packs,
        "posted": False,
    }

    if post:
        try:
            from apps.council import report_cast
            from apps.council.config import load_config

            cast = report_cast.notify_report(
                "energy",
                transcript=body,
                title="Energy desk — Rear Shed",
                photo=frame,
                cfg=load_config(),
            )
            report["cast"] = {k: cast.get(k) for k in ("ok", "skipped", "detail", "ids", "kind")}
            report["posted"] = bool(cast.get("ok") and not cast.get("skipped"))
        except Exception as e:
            report["cast_error"] = f"{type(e).__name__}: {e}"
            report["ok"] = False

    st["last_ok"] = time.time() if report.get("ok") else st.get("last_ok")
    st["last_path"] = str(out_md)
    st["last_caption"] = caption[:500]
    st["last_skip"] = None if report.get("ok") else (report.get("cast_error") or "failed")
    st["last_frame"] = str(frame) if frame else None
    save_state(st)
    return report


async def run() -> None:
    st = load_state()
    if not st.get("auto", True):
        log.info("energy-report skipped auto_off")
        return
    report = build_report(post=True, vision=True)
    log.info(
        "energy-report ok=%s posted=%s frame=%s",
        report.get("ok"),
        report.get("posted"),
        report.get("frame"),
    )


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(description="Carly energy report (latest panels still).")
    p.add_argument("--post", action="store_true", help="Post to Telegram as Carly.")
    p.add_argument("--no-vision", action="store_true")
    p.add_argument("--auto", choices=("on", "off"))
    p.add_argument("--status", action="store_true")
    args = p.parse_args(argv)
    if args.auto:
        st = load_state()
        st["auto"] = args.auto == "on"
        save_state(st)
        print(json.dumps(st, indent=2))
        return 0
    if args.status:
        print(json.dumps(load_state(), indent=2))
        return 0
    report = build_report(post=bool(args.post), vision=not args.no_vision)
    print(json.dumps(report, indent=2, default=str))
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
