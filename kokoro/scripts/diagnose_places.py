#!/usr/bin/env python3
"""Generate one-name clips for Heart, Echo, Nova and print Kokoro phonemes."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate import SPEEDS, STORE, _pipeline, synthesize  # noqa: E402
from hawaiian_lexicon import DIAGNOSE_NAMES, PLACE_PHONEMES  # noqa: E402
from speakable import speakable as to_speak  # noqa: E402

OUT = Path("/tmp/hawaiian-voice-check")
VOICES = ("af_heart", "am_echo", "af_nova")


def phonemes_of(text: str, voice: str) -> str:
    voice_pack = STORE / "voices" / f"{voice}.pt"
    voice_arg = str(voice_pack) if voice_pack.is_file() else voice
    parts = []
    for _gs, ps, _audio in _pipeline()(text, voice=voice_arg, speed=SPEEDS.get(voice, 1.0)):
        if ps:
            parts.append(ps)
    return " ".join(parts)


def main() -> int:
    _pipeline()
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for voice in VOICES:
        vdir = OUT / voice
        vdir.mkdir(parents=True, exist_ok=True)
        board = []
        for name in DIAGNOSE_NAMES:
            spoken = to_speak(name + ".")
            wav = vdir / (name.replace(" ", "-") + ".wav")
            result = synthesize(name + ".", wav, voice=voice)
            ph = phonemes_of(spoken, voice)
            expect = " ".join(PLACE_PHONEMES.get(p, "") for p in name.split()) or PLACE_PHONEMES.get(name, "")
            rows.append(
                {
                    "voice": voice,
                    "name": name,
                    "speak": result.get("speak"),
                    "phonemes": ph,
                    "expect": expect,
                    "wav": str(wav),
                    "ok": result.get("ok"),
                }
            )
            board.append(name + ".")
        board_text = " ".join(board)
        synthesize(board_text, vdir / "_board.wav", voice=voice)
        print(voice, "board", vdir / "_board.wav")
    print(json.dumps(rows, indent=2, ensure_ascii=False))
    return 0 if all(r.get("ok") for r in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
