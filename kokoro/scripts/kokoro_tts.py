"""Origin-side Kokoro TTS. Always writes WAV. Never clip-stitch. Never xAI."""
from __future__ import annotations

import json
import logging
import os
import re
import subprocess
import tempfile
from pathlib import Path

from apps.core import config

log = logging.getLogger("ava.kokoro")

SKILL = Path.home() / ".ollama" / "skills" / "kokoro"
VENV_PY = SKILL / "store" / "venv" / "bin" / "python"
GENERATE = SKILL / "scripts" / "generate.py"
SAMPLE_RATE = 24000

_TOKEN_WORDS = {
    "hawaiian_standard_time": "Hawaiian Standard Time",
    "state_of_charge": "state of charge",
    "end_of_status": "End of status.",
    "this_is": "This is",
    "ava_core": "Ava Core",
    "ava": "Ava",
    "ayeva": "Ayeva",
    "root_record": "Root Record",
    "oclock": "o'clock",
    "am": "AM",
    "pm": "PM",
    "phrase_hourly_solar": "Hourly solar report.",
    "phrase_ecoflow_down": "EcoFlow is offline.",
    "phrase_remaining_tasks": "Remaining tasks.",
    "phrase_all_systems_running": "All systems running.",
    "satellite_connection": "Satellite connection restored.",
}


def venv_ready() -> bool:
    return VENV_PY.is_file() and GENERATE.is_file()


def tokens_to_speech(script: str) -> str:
    """Turn leftover clip-token scripts into spoken English. Pass prose through."""
    raw = (script or "").strip()
    if not raw:
        return ""
    if raw.count("_") <= 1 and any(ch in raw for ch in ".!?"):
        return " ".join(raw.split())
    parts: list[str] = []
    for tok in raw.split():
        key = re.sub(r"[^a-z0-9_]", "", tok.lower())
        if key in _TOKEN_WORDS:
            parts.append(_TOKEN_WORDS[key])
            continue
        parts.append(tok.replace("_", " "))
    return " ".join(parts)


def generate_wav(text: str, out_path: Path, *, voice: str | None = None) -> dict:
    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    dest.parent.mkdir(parents=True, exist_ok=True)
    # generate.py applies speakable once — do not pre-process here or clocks/HST double-mangle.
    spoken = " ".join((text or "").split()).strip()
    if not spoken:
        return {"ok": False, "detail": "empty_text", "engine": "kokoro", "wav": str(dest)}
    if not venv_ready():
        return {
            "ok": False,
            "detail": "kokoro_venv_missing",
            "engine": "kokoro",
            "wav": str(dest),
        }
    voice = voice or os.getenv("KOKORO_VOICE") or getattr(config, "TTS_VOICE", None)
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as tmp:
        tmp.write(spoken)
        tmp_path = Path(tmp.name)
    try:
        cmd = [
            str(VENV_PY),
            str(GENERATE),
            "--text-file",
            str(tmp_path),
            "--out",
            str(dest),
        ]
        if voice:
            cmd += ["--voice", str(voice)]
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=180,
            cwd=str(SKILL),
        )
    except subprocess.TimeoutExpired:
        return {"ok": False, "detail": "timeout", "engine": "kokoro", "wav": str(dest)}
    finally:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass
    payload: dict = {}
    blob = (proc.stdout or "").strip()
    if blob:
        try:
            payload = json.loads(blob.splitlines()[-1])
        except json.JSONDecodeError:
            payload = {}
    if proc.returncode != 0 or not payload.get("ok") or not dest.is_file():
        err = (proc.stderr or payload.get("detail") or blob or f"exit {proc.returncode}")[:400]
        log.warning("Kokoro generate failed: %s", err)
        return {
            "ok": False,
            "detail": str(err),
            "engine": "kokoro",
            "wav": str(dest),
        }
    payload["engine"] = "kokoro"
    payload["wav"] = str(dest)
    payload["mp3"] = str(dest)
    return payload
