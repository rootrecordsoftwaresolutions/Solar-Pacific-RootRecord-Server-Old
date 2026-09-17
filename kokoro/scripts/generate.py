#!/usr/bin/env python3
"""Kokoro-82M → 16-bit PCM WAV. Run with the skill venv Python."""
from __future__ import annotations

import argparse
import json
import os
import sys
import warnings
from pathlib import Path

STORE = Path.home() / ".ollama" / "skills" / "kokoro" / "store" / "Kokoro-82M"
sys.path.insert(0, str(Path(__file__).resolve().parent))
from speakable import speakable
from hawaiian_lexicon import lexicon_entries
SAMPLE_RATE = 24000
DEFAULT_VOICE = "af_heart"
SPEEDS = {
    "af_heart": 0.82,
    "am_echo": 0.92,
    "af_nova": 0.74,
}
VOICE_ALIASES = {
    "ara": "af_heart",
    "local": "af_heart",
    "kokoro": "af_heart",
    "cloud": "af_heart",
    "ava": "af_heart",
    "heart": "af_heart",
    "bruce": "am_echo",
    "echo": "am_echo",
    "carly": "af_nova",
    "nova": "af_nova",
}

_PIPELINE = None

# Ayeva / Ava = AY-vah (Kokoro US: A = /eɪ/)
AYEVA_PHONEME = "ˈAvə"


def apply_lexicon(pipeline) -> None:
    g2p = getattr(pipeline, "g2p", None)
    lexicon = getattr(g2p, "lexicon", None) if g2p is not None else None
    if lexicon is None or not hasattr(lexicon, "golds"):
        return
    extra = {
        "Ava": AYEVA_PHONEME,
        "Ayeva": AYEVA_PHONEME,
        "Ava's": AYEVA_PHONEME + "z",
        "Ayeva's": AYEVA_PHONEME + "z",
        "Avaivy": AYEVA_PHONEME + "ˈIvi",
        "avaivy": AYEVA_PHONEME + "ˈIvi",
    }
    extra.update(lexicon_entries())
    if hasattr(lexicon, "grow_dictionary"):
        extra = lexicon.grow_dictionary(extra)
    lexicon.golds.update(extra)


def resolve_voice(name: str | None) -> str:
    raw = (name or os.getenv("KOKORO_VOICE") or os.getenv("TTS_VOICE") or DEFAULT_VOICE).strip()
    key = raw.lower()
    return VOICE_ALIASES.get(key, raw)


def _pipeline():
    global _PIPELINE
    if _PIPELINE is None:
        os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
        from kokoro import KModel, KPipeline

        warnings.filterwarnings("ignore")
        weights = STORE / "kokoro-v1_0.pth"
        cfg = STORE / "config.json"
        if not weights.is_file() or not cfg.is_file():
            raise FileNotFoundError(f"Kokoro-82M missing under {STORE}")
        device = "cpu"
        model = KModel(
            repo_id="hexgrad/Kokoro-82M",
            config=str(cfg),
            model=str(weights),
        ).to(device).eval()
        _PIPELINE = KPipeline(
            lang_code="a",
            repo_id="hexgrad/Kokoro-82M",
            model=model,
            device=device,
        )
        apply_lexicon(_PIPELINE)
    return _PIPELINE


def synthesize(text: str, out_path: Path, *, voice: str | None = None, speed: float | None = None) -> dict:
    spoken = speakable(text)
    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not spoken:
        return {"ok": False, "detail": "empty_text", "wav": str(dest)}

    import numpy as np
    import soundfile as sf

    voice_name = resolve_voice(voice)
    voice_pack = STORE / "voices" / f"{voice_name}.pt"
    voice_arg = str(voice_pack) if voice_pack.is_file() else voice_name
    rate = float(speed) if speed is not None else SPEEDS.get(voice_name, 1.0)
    chunks = []
    for _gs, _ps, audio in _pipeline()(spoken, voice=voice_arg, speed=rate):
        if audio is None:
            continue
        chunks.append(np.asarray(audio, dtype=np.float32))
    if not chunks:
        return {"ok": False, "detail": "no_audio", "wav": str(dest), "voice": voice_name}
    wave = np.concatenate(chunks) if len(chunks) > 1 else chunks[0]
    sf.write(str(dest), wave, SAMPLE_RATE, subtype="PCM_16")
    return {
        "ok": True,
        "engine": "kokoro",
        "voice": voice_name,
        "speed": rate,
        "wav": str(dest),
        "speak": spoken,
        "sample_rate": SAMPLE_RATE,
        "chars": len(spoken),
        "bytes": dest.stat().st_size if dest.is_file() else 0,
    }


def main() -> int:
    p = argparse.ArgumentParser(description="Kokoro-82M text to WAV")
    p.add_argument("--text", default="")
    p.add_argument("--text-file")
    p.add_argument("--out", required=True)
    p.add_argument("--voice")
    p.add_argument("--speed", type=float)
    args = p.parse_args()
    text = args.text or ""
    if args.text_file:
        text = Path(args.text_file).read_text(encoding="utf-8")
    if not text.strip() and not sys.stdin.isatty():
        text = sys.stdin.read()
    result = synthesize(text, Path(args.out), voice=args.voice, speed=args.speed)
    print(json.dumps(result))
    return 0 if result.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
