"""Spoken WAV via Kokoro-82M. Clip stitch is disabled (see ~/.ollama/disabled/clip-tts)."""
from __future__ import annotations

import logging
from pathlib import Path

from apps.core import config
from apps.voice.kokoro_tts import generate_wav, tokens_to_speech

log = logging.getLogger("ava.local_tts")

GENERATED = config.GENERATED_DIR

WEEKDAYS = (
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
)
MONTHS = (
    "january",
    "february",
    "march",
    "april",
    "may",
    "june",
    "july",
    "august",
    "september",
    "october",
    "november",
    "december",
)


def date_tokens(now) -> list[str]:
    bits = [WEEKDAYS[now.weekday()], MONTHS[now.month - 1], str(int(now.day))]
    bits.append("week")
    return bits


def clock_tokens(hour: int, minute: int) -> list[str]:
    mer = "am" if hour < 12 else "pm"
    if hour == 0 and minute == 0:
        return ["midnight", "hawaiian_standard_time"]
    if hour == 12 and minute == 0:
        return ["noon", "hawaiian_standard_time"]
    h12 = hour % 12
    if h12 == 0:
        h12 = 12
    bits = [str(h12)]
    if minute:
        bits.append(str(int(minute)))
    else:
        bits.append("oclock")
    bits += [mer, "hawaiian_standard_time"]
    return bits


def spoken_clock(hour: int, minute: int) -> str:
    try:
        from apps.voice.speakable import spoken_clock as _spoken

        return f"{_spoken(hour, minute)} Hawaiian Standard Time"
    except Exception:
        return tokens_to_speech(" ".join(clock_tokens(hour, minute)))


def speak_script(
    script: str,
    out_path: Path,
    *,
    silence_ms: int | None = None,
    kind: str | None = None,
) -> dict:
    """Kokoro WAV for a locked desk. Requires kind. silence_ms is ignored."""
    from apps.voice.speakers import speak_report

    if not kind:
        log.warning("speak_script refused — no kind")
        return {"ok": False, "skipped": True, "detail": "no_kind"}
    built = speak_report(kind, script, Path(out_path))
    if built.get("ok"):
        built.setdefault("clips", 0)
        built.setdefault("missing", [])
        dest = Path(built.get("wav") or out_path)
        built["mp3"] = str(dest)
        built["wav"] = str(dest)
    return built


def build_time_announcement(hour: int, minute: int, out_path: Path, now=None) -> dict:
    """Bell (if present) + Kokoro spoken clock → one WAV."""
    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    dest.parent.mkdir(parents=True, exist_ok=True)
    voice = dest.with_name(dest.stem + "-voice.wav")
    from apps.voice.speakers import kokoro_voice_for

    spoken = f"It's {spoken_clock(hour, minute)}."
    built = generate_wav(spoken, voice, voice=kokoro_voice_for("chime"))
    if not built.get("ok"):
        return built
    try:
        from apps.voice.clips import SOUNDS_DIR, concatenate_clips

        bell = SOUNDS_DIR / "futuristic_bell.mp3"
        if not bell.is_file():
            bell = SOUNDS_DIR / "futuristic_bell.wav"
        parts = []
        if bell.is_file():
            parts.append(bell)
        parts.append(voice)
        if len(parts) == 1:
            dest.write_bytes(voice.read_bytes())
        else:
            concatenate_clips(parts, dest)
    except Exception as e:
        log.warning("chime concat skipped: %s", e)
        dest.write_bytes(voice.read_bytes())
    return {
        "ok": True,
        "engine": "kokoro",
        "mp3": str(dest),
        "wav": str(dest),
        "clips": 1,
        "script": spoken,
        "bytes": dest.stat().st_size if dest.is_file() else 0,
        "missing": [],
    }
