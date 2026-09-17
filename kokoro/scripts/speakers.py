"""Locked Kokoro voices and live-data gate for spoken reports.

Bruce = Echo (am_echo)
Ava = Heart (af_heart)
Carly = Nova (af_nova)

Do not write WAV unless the text carries live measured facts.
Clip-stitch is off.
"""
from __future__ import annotations

import logging
import re
import shutil
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

log = logging.getLogger("ava.speakers")
HST = ZoneInfo("Pacific/Honolulu")


def is_current_audio(path: Path) -> bool:
    stem = Path(path).stem.lower()
    return (
        stem.endswith("-current")
        or stem.endswith("_current")
        or stem == "nws_official_statement"
        or stem == "kilauea_current"
    )


def retire_current(path: Path) -> Path | None:
    """Move a live current file aside, named with that file's generation time."""
    path = Path(path)
    if not path.is_file() or path.stat().st_size <= 0:
        return None
    stamp = datetime.fromtimestamp(path.stat().st_mtime, HST).strftime("%Y%m%d-%H%M")
    stem = path.stem
    base = re.sub(r"[-_][Cc]urrent$", "", stem)
    dest = path.with_name(f"{base}-{stamp}{path.suffix}")
    n = 0
    while dest.exists() and dest.resolve() != path.resolve():
        n += 1
        dest = path.with_name(f"{base}-{stamp}-{n}{path.suffix}")
    if dest.resolve() == path.resolve():
        return None
    try:
        path.replace(dest)
    except OSError:
        return None
    for extra in (".read.txt", ".speak.txt"):
        side = path.with_suffix(extra)
        if side.is_file():
            try:
                side.replace(dest.with_suffix(extra))
            except OSError:
                pass
    return dest


AGENTS = {
    "ava": {"name": "Ava", "kokoro": "af_heart"},
    "bruce": {"name": "Bruce", "kokoro": "am_echo"},
    "carly": {"name": "Carly", "kokoro": "af_nova"},
}

# Automated spoken desks. Keep the three loads even.
KIND_AGENT = {
    "morning": "ava",
    "midday": "ava",
    "evening": "ava",
    "late": "ava",
    "summary": "ava",
    "weather": "ava",
    "nws": "ava",
    "chime": "ava",
    "official": "ava",
    "boot": "ava",
    "solar": "bruce",
    "system": "bruce",
    "remaining": "bruce",
    "hourly": "bruce",
    "earthquake": "carly",
    "kilauea": "carly",
    "hurricane": "carly",
    "alerts": "carly",
    "security": "carly",
    "bandwidth": "carly",
    "net": "carly",
}

_DEAD = (
    "not live",
    "offline stub",
    "facts not live",
    "no data",
    "end of status",
)


def agent_for(kind: str) -> str:
    key = (kind or "").strip().lower()
    if key in AGENTS:
        return key
    return KIND_AGENT.get(key, "ava")


def kokoro_voice_for(kind: str) -> str:
    return AGENTS[agent_for(kind)]["kokoro"]


def display_name(kind: str) -> str:
    return AGENTS[agent_for(kind)]["name"]


def _dead_line(text: str) -> bool:
    low = (text or "").strip().lower()
    if not low or len(low) < 12:
        return True
    if len(low) < 400:
        if "ecoflow: down" in low or "host: down" in low or "kilauea: down" in low:
            return True
        if "weather: down" in low:
            return True
    if any(m in low for m in _DEAD) and not re.search(r"\d", low):
        return True
    if "this is the ava core root record" in low and not re.search(r"\d", low):
        return True
    return False


def is_live(kind: str, text: str) -> bool:
    """True only when the spoken body has live facts for that desk."""
    raw = " ".join((text or "").split()).strip()
    key = (kind or "").strip().lower()
    if key in AGENTS:
        return len(re.sub(r"\s+", "", raw)) >= 8
    if _dead_line(raw):
        return False
    if key in {"chime"}:
        return bool(re.search(r"\d", raw) or "o'clock" in raw.lower() or "noon" in raw.lower())
    if key in {"solar"}:
        return bool(re.search(r"\d+\s*%", raw) or re.search(r"\d+\s*w\b", raw, re.I))
    if key in {"system", "hourly"}:
        return bool(re.search(r"(cpu|ram|memory|npu|gpu).{0,12}\d+\s*%", raw, re.I) or re.search(r"\d+\s*%", raw))
    if key in {"weather", "nws", "official"}:
        return "nws" in raw.lower() or bool(re.search(r"\d", raw)) or "no active" in raw.lower()
    if key in {"kilauea"}:
        return "kilauea" in raw.lower() and "down" not in raw.lower()
    if key in {"security"}:
        return bool(re.search(r"\d", raw)) and (
            "security" in raw.lower()
            or "firewall" in raw.lower()
            or "sign-in" in raw.lower()
            or "listener" in raw.lower()
        )
    if key in {"bandwidth", "net"}:
        return bool(re.search(r"\d", raw)) and (
            "megabyte" in raw.lower()
            or "gigabyte" in raw.lower()
            or "kilobyte" in raw.lower()
            or "bytes" in raw.lower()
        )
    if key in {"earthquake"}:
        return "earthquake" in raw.lower() and ("usgs" in raw.lower() or bool(re.search(r"\d", raw)))
    if key in {"hurricane"}:
        return ("storm" in raw.lower() or "hurricane" in raw.lower() or "tropical" in raw.lower()) and (
            bool(re.search(r"\d", raw)) or "no named" in raw.lower() or "quiet" in raw.lower()
        )
    if key in {"remaining"}:
        return bool(re.search(r"\d", raw))
    # Daily public reports: need at least one measured number.
    if key in {"morning", "midday", "evening", "late", "summary", "boot"}:
        return bool(re.search(r"\d", raw))
    return bool(re.search(r"\d", raw))


_NAME_LEAD = re.compile(
    r"(?i)^(?:this is\s+)?(?:ava ivy|ava|bruce monitor|bruce|carly mal|carly)\s*[,.]\s+"
)


def without_name(text: str) -> str:
    """Spoken reports use the locked Kokoro voice. Do not say the agent name."""
    body = " ".join((text or "").split()).strip()
    while True:
        nxt = _NAME_LEAD.sub("", body, count=1).strip()
        if nxt == body:
            return body
        body = nxt


def with_intro(kind: str, text: str) -> str:
    return without_name(text)


def speak_report(kind: str, text: str, dest: Path, *, intro: bool = False) -> dict:
    """Kokoro WAV for a locked speaker. Does not write dest unless live facts exist."""
    from apps.voice.kokoro_tts import generate_wav

    dest = Path(dest)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    spoken = without_name(text)
    if not is_live(kind, spoken):
        log.info("%s skip WAV — no live facts", kind)
        return {
            "ok": False,
            "skipped": True,
            "detail": "no_live_data",
            "engine": "kokoro",
            "agent": agent_for(kind),
            "voice": kokoro_voice_for(kind),
            "wav": str(dest),
            "script": spoken,
        }
    if is_current_audio(dest):
        retire_current(dest)
    built = generate_wav(spoken, dest, voice=kokoro_voice_for(kind))
    built["agent"] = agent_for(kind)
    built["read"] = spoken
    # Prefer the exact script Kokoro spoke (already speakable once in generate.py).
    speak_body = str(built.get("speak") or built.get("script") or "").strip()
    if not speak_body:
        try:
            from apps.voice.speakable import speakable

            speak_body = speakable(spoken)
        except Exception:
            speak_body = spoken
    built["script"] = speak_body
    built["speak"] = speak_body
    if built.get("ok"):
        dest.with_suffix(".read.txt").write_text(spoken.strip() + "\n", encoding="utf-8")
        dest.with_suffix(".speak.txt").write_text(speak_body.strip() + "\n", encoding="utf-8")
    return built


def publish_current(src: Path, *currents: Path) -> None:
    if not src.is_file() or src.stat().st_size <= 0:
        return
    src = Path(src)
    for dest in currents:
        dest = Path(dest)
        dest.parent.mkdir(parents=True, exist_ok=True)
        retire_current(dest)
        try:
            shutil.copy2(src, dest)
        except OSError:
            continue
        for kind in (".read.txt", ".speak.txt"):
            extra = src.with_suffix(kind)
            if extra.is_file():
                try:
                    shutil.copy2(extra, dest.with_suffix(kind))
                except OSError:
                    pass
        legacy = dest.with_suffix(".mp3")
        if legacy.is_file():
            try:
                legacy.unlink()
            except OSError:
                pass
