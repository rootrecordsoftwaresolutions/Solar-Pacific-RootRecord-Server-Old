"""TTS facade — Kokoro-82M WAV only. Clip stitch and xAI Ara are off."""

from __future__ import annotations

import logging
from pathlib import Path

from apps.core import config
from apps.voice.kokoro_tts import generate_wav

log = logging.getLogger("ava.tts")


def synthesize(text: str, out_path: Path, *, force_grok: bool = False) -> Path | None:
    mode = (config.VOICE_MODE or "kokoro").strip().lower()
    if mode == "disabled":
        return None
    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    built = generate_wav(text, dest)
    if built.get("ok"):
        return dest
    log.error("Kokoro TTS failed: %s", built.get("detail"))
    return None
