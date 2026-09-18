#!/usr/bin/env python3
"""Carly energy report — latest Rear Shed still + pack facts + vision caption.

Does not cycle River car DC. Uses the most recent saved panels frame.
"""
from __future__ import annotations

import json
import logging
import re
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


def load_panels_context() -> dict[str, Any]:
    path = STORE / "panels_context.json"
    base: dict[str, Any] = {
        "osd_name_preferred": "Solar Panels",
        "osd_name_legacy": "Rear Shed",
        "mount": "wood frame",
        "evening_position": "upright",
        "evening_note": "Upright on the wood frame is the normal evening stow position.",
        "site": "Hawaiʻi outdoor solar panels in tall grass",
        "operator_live_notes": [],
    }
    if path.is_file():
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                base.update(raw)
        except Exception:
            pass
    md = STORE / "PANELS_CONTEXT.md"
    base["context_md"] = md.read_text(encoding="utf-8") if md.is_file() else ""
    return base


def set_operator_note(note: str) -> dict[str, Any]:
    """Append a short live operator note (e.g. pouring rain, lightning)."""
    ctx = load_panels_context()
    notes = list(ctx.get("operator_live_notes") or [])
    note = " ".join(str(note or "").split()).strip()
    if note:
        notes.append({"ts": time.time(), "text": note[:300]})
        notes = notes[-12:]
    ctx["operator_live_notes"] = notes
    path = STORE / "panels_context.json"
    path.write_text(json.dumps({k: v for k, v in ctx.items() if k != "context_md"}, indent=2) + "\n", encoding="utf-8")
    return ctx


def _blend_prompt(ctx: dict[str, Any]) -> str:
    name = str(ctx.get("osd_name_preferred") or "Solar Panels")
    mount = str(ctx.get("mount") or "wood frame")
    evening = str(ctx.get("evening_note") or "")
    live = []
    for row in ctx.get("operator_live_notes") or []:
        if isinstance(row, dict) and row.get("text"):
            if time.time() - float(row.get("ts") or 0) < 7200:
                live.append(str(row["text"]))
    live_bit = (" Operator reports now: " + "; ".join(live) + ".") if live else ""
    return (
        f"This is OUR site cam on the {name} array in Hawaiʻi — ground-mounted on a {mount}. "
        f"You already know that; do not introduce the scene like a tourist. {evening} "
        "Return ONLY ops deltas as short clauses separated by semicolons, not full scenic sentences. "
        "Allowed topics: panel angle (upright evening stow vs day tilt); rain/wet glare/fogged lens; "
        "soaked ground; storm-dark vs bright overcast; debris or shade on panels; anything unsafe. "
        "Forbidden: 'the image shows', 'rural setting', 'appears to be', 'likely a garden', "
        "generic landscape filler, inventing lightning flashes, inventing watts or SOC."
        f"{live_bit}"
    )


def caption_still(path: Path) -> str:
    """Vision caption via look model — ops deltas only, site already known."""
    try:
        from apps.core.services import ollama as ollama_svc
    except Exception:
        try:
            sys.path.insert(0, str(Path.home() / ".ollama" / "skills" / "ollama-client" / "scripts"))
            import ollama as ollama_svc  # type: ignore
        except Exception as e:
            return f"(vision offline: {type(e).__name__})"
    ctx = load_panels_context()
    prompt = _blend_prompt(ctx)
    thumb = _vision_still(path)
    try:
        cap = ollama_svc.look_sync(prompt, [thumb], timeout=120)
    except Exception as e:
        return f"(vision miss: {type(e).__name__})"
    text = (cap or "").strip() or "(no caption)"
    low = text.lower()
    if "metal" in low and "wood" not in low:
        text += " (Site note: mount is wood frame.)"
    return text


def distill_cam_read(caption: str, *, ctx: dict[str, Any] | None = None) -> str:
    """Turn vision prose into ops notes Carly already understands."""
    ctx = ctx or load_panels_context()
    raw = " ".join((caption or "").split()).strip()
    if not raw or raw.startswith("("):
        return ""
    low = raw.lower()
    bits: list[str] = []
    # Position
    if any(w in low for w in ("upright", "vertical", "evening stow", "stow")):
        bits.append("panels in evening upright stow on the wood frame")
    elif any(w in low for w in ("flat", "tilted toward", "day tilt", "angled toward the sky")):
        bits.append("panels look day-tilted, not stowed upright")
    # Weather / lens
    if any(w in low for w in ("rain", "streak", "mist", "pouring", "spray")):
        bits.append("rain on the lens or array")
    if any(w in low for w in ("glare", "reflect", "washed", "fogged", "flare", "wet")):
        bits.append("heavy wet glare — not clear-sun production light")
    if any(w in low for w in ("soak", "mud", "standing water")):
        bits.append("ground soaked")
    if "overcast" in low or "storm" in low or "cloud" in low:
        bits.append("overcast / storm light")
    if "lightning" in low and "no" not in low.split("lightning")[0][-20:]:
        # only if vision claimed a flash (rare)
        if "no visible lightning" not in low and "no lightning" not in low:
            bits.append("possible flash in frame")
    # Mount confirmation
    if "wood" in low:
        bits.append("wood frame confirmed in frame")
    # Operator live notes always win for weather narrative
    live = [
        str(r.get("text"))
        for r in (ctx.get("operator_live_notes") or [])
        if isinstance(r, dict) and r.get("text") and time.time() - float(r.get("ts") or 0) < 7200
    ]
    if live and not bits:
        return "; ".join(live)
    if not bits:
        # strip tourist openers
        cleaned = re.sub(
            r"(?i)^(the image shows|this (is|shows|appears)|in (the|this) (image|photo|frame)[,:]?\s*)+",
            "",
            raw,
        ).strip()
        return cleaned[:280]
    return "; ".join(bits)


def _speak_pack_line(line: str) -> str:
    """Delta 2 · 98% · PV 0 W · out 0 W · (ble, 88s) → Carly ops English."""
    s = str(line)
    s = re.sub(r"\s*\([^)]*\)\s*$", "", s)
    parts = [p.strip() for p in s.split("·")]
    if not parts:
        return ""
    name = parts[0]
    soc = next((p for p in parts[1:] if "%" in p), "")
    pv = next((p for p in parts[1:] if p.upper().startswith("PV")), "")
    out = next((p for p in parts[1:] if p.lower().startswith("out")), "")
    chunks = [name]
    if soc:
        chunks.append(f"at {soc.replace('%', ' percent').strip()}")
    if pv:
        w = re.sub(r"(?i)pv\s*", "", pv).strip()
        w = re.sub(r"(?i)\s*w\b", " watts", w)
        chunks.append(f"array in {w}" if "0" in w else f"array making {w}")
    if out:
        w = re.sub(r"(?i)out\s*", "", out).strip()
        w = re.sub(r"(?i)\s*w\b", " watts", w)
        chunks.append(f"output {w}")
    return ", ".join(chunks)


def spoken_script(*, caption: str, packs: list[str], weather: str | None) -> str:
    """Carly ops voice — she already knows the site; no tourist narration."""
    ctx = load_panels_context()
    now = datetime.now(HST)
    hour = now.hour
    stow = "evening upright stow" if hour >= 17 or hour < 7 else "day tilt check"
    parts = [
        f"Energy desk, {now.strftime('%-I:%M %p')} Hawaiian Standard Time.",
        f"Solar Panels cam — wood-frame ground array, expecting {stow}.",
    ]
    live = [
        str(r.get("text"))
        for r in (ctx.get("operator_live_notes") or [])
        if isinstance(r, dict) and r.get("text") and time.time() - float(r.get("ts") or 0) < 7200
    ]
    if live:
        parts.append("Operator: " + "; ".join(live).rstrip(".; ") + ".")

    cam = distill_cam_read(caption, ctx=ctx)
    if cam:
        # Don't re-say wood frame / evening if we already framed it
        cam = re.sub(r"(?i)\s*;?\s*wood frame confirmed in frame", "", cam).strip(" ;")
        if "evening upright" in parts[1].lower() or "evening upright stow" in " ".join(parts).lower():
            cam = re.sub(
                r"(?i)\s*;?\s*panels in evening upright stow on the wood frame",
                "",
                cam,
            ).strip(" ;")
        if cam:
            parts.append("Cam: " + cam.rstrip(".; ") + ".")

    # Packs in known order — Delta bank + River car/loads
    for line in packs:
        spoken = _speak_pack_line(line)
        if spoken:
            parts.append(spoken + ".")

    # Tie PV zero to storm when relevant
    blob = " ".join(packs).lower() + " " + (caption or "").lower() + " " + " ".join(live).lower()
    packed = blob.replace(" ", "")
    if "pv0" in packed or "solar0" in packed:
        if any(w in blob for w in ("rain", "storm", "overcast", "glare", "lightning", "pouring")):
            parts.append("Zero array watts matches the storm cover — not a wiring fault from this still.")

    if weather:
        w = re.sub(r"[*_`#]", "", weather)
        w = re.sub(r"(?i)^weather\s*\(\d+m\)\s*:\s*", "", w)
        w = re.sub(r"\s+", " ", w).strip()
        if w:
            parts.append("NWS: " + w.rstrip(".; ") + ".")

    parts.append("Carly, energy desk out.")
    return " ".join(parts)


def build_transcript(*, caption: str, packs: list[str], weather: str | None, frame: Path | None) -> str:
    now = datetime.now(HST)
    ctx = load_panels_context()
    name = str(ctx.get("osd_name_preferred") or "Solar Panels")
    cam = distill_cam_read(caption, ctx=ctx) or caption
    lines = [
        f"Energy desk — {now.strftime('%Y-%m-%d %H:%M')} HST",
        "",
        f"{name} cam · wood-frame ground array · evening upright is normal stow.",
    ]
    live = [
        str(r.get("text"))
        for r in (ctx.get("operator_live_notes") or [])
        if isinstance(r, dict) and r.get("text") and time.time() - float(r.get("ts") or 0) < 7200
    ]
    if live:
        lines.append("Operator: " + "; ".join(live))
    if frame:
        age_m = int((time.time() - frame.stat().st_mtime) / 60)
        lines.append(f"Still age: {age_m}m")
    lines.append("")
    lines.append("Cam (ops):")
    lines.append(cam)
    if caption and caption != cam and not caption.startswith("("):
        lines.append("")
        lines.append("Vision raw:")
        lines.append(caption)
    lines.append("")
    if packs:
        lines.append("Packs:")
        lines.extend(f"- {p}" for p in packs)
        lines.append("")
    if weather:
        lines.append(weather)
        lines.append("")
    lines.append("Carly — energy / site security. Reply with notes if this should be better.")
    return "\n".join(lines).strip()


def render_voice(spoken: str) -> dict[str, Any]:
    """Kokoro WAV as Carly (energy desk)."""
    from apps.voice.speakers import publish_current, speak_report

    stamp = datetime.now(HST).strftime("%Y%m%d-%H%M")
    audio_dir = STORE / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    dest = audio_dir / f"energy-{stamp}.wav"
    current = audio_dir / "energy-current.wav"
    result = speak_report("energy", spoken, dest)
    if result.get("ok") and dest.is_file():
        publish_current(dest, current)
        result["current"] = str(current)
    return result


def build_report(*, post: bool = True, vision: bool = True, voice: bool = True) -> dict[str, Any]:
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
    spoken = spoken_script(caption=caption, packs=packs, weather=weather)

    out_md = STORE / f"energy-{datetime.now(HST).strftime('%Y%m%d-%H%M')}.md"
    out_md.write_text(body + "\n", encoding="utf-8")
    latest_md = STORE / "energy-latest.md"
    latest_md.write_text(body + "\n", encoding="utf-8")
    (STORE / "energy-latest.speak.txt").write_text(spoken + "\n", encoding="utf-8")
    if frame:
        pointer = STORE / "LATEST_FRAME.txt"
        pointer.write_text(str(frame) + "\n", encoding="utf-8")

    report: dict[str, Any] = {
        "ok": True,
        "path": str(out_md),
        "frame": str(frame) if frame else None,
        "caption": caption,
        "packs": packs,
        "spoken": spoken,
        "posted": False,
        "voice": None,
    }

    wav_path: Path | None = None
    if voice:
        try:
            voice_out = render_voice(spoken)
            report["voice"] = {
                k: voice_out.get(k)
                for k in ("ok", "skipped", "detail", "wav", "voice", "agent", "current")
            }
            if voice_out.get("ok") and voice_out.get("wav"):
                wav_path = Path(str(voice_out["wav"]))
            elif not voice_out.get("ok"):
                report["voice_error"] = voice_out.get("detail") or "voice_failed"
        except Exception as e:
            report["voice_error"] = f"{type(e).__name__}: {e}"

    if post:
        try:
            from apps.council import report_cast
            from apps.council.config import load_config

            cast = report_cast.notify_report(
                "energy",
                transcript=body,
                title="Energy desk — Solar Panels",
                photo=frame,
                audio=wav_path if wav_path and wav_path.is_file() else None,
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
    st["last_spoken"] = spoken[:800]
    st["last_wav"] = str(wav_path) if wav_path else None
    st["last_skip"] = None if report.get("ok") else (report.get("cast_error") or report.get("voice_error") or "failed")
    st["last_frame"] = str(frame) if frame else None
    save_state(st)
    return report


async def run() -> None:
    st = load_state()
    if not st.get("auto", True):
        log.info("energy-report skipped auto_off")
        return
    report = build_report(post=True, vision=True, voice=True)
    log.info(
        "energy-report ok=%s posted=%s voice=%s frame=%s",
        report.get("ok"),
        report.get("posted"),
        (report.get("voice") or {}).get("ok"),
        report.get("frame"),
    )


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(description="Carly energy report (latest panels still).")
    p.add_argument("--post", action="store_true", help="Post to Telegram as Carly.")
    p.add_argument("--no-vision", action="store_true")
    p.add_argument("--no-voice", action="store_true", help="Skip Kokoro WAV.")
    p.add_argument("--note", default="", help="Operator live weather note (rain, lightning…).")
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
    if args.note:
        set_operator_note(args.note)
    # --post implies voice+vision unless disabled; bare run still builds voice
    want_post = bool(args.post)
    report = build_report(
        post=want_post,
        vision=not args.no_vision,
        voice=not args.no_voice,
    )
    print(json.dumps(report, indent=2, default=str))
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
