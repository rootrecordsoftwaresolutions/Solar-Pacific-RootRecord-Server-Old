#!/usr/bin/env python3
"""One WAV per official American Kokoro voice, saying Ayeva + the voice name."""
from __future__ import annotations

import json
import sys
from pathlib import Path

SKILL = Path.home() / ".ollama" / "skills" / "kokoro"
sys.path.insert(0, str(SKILL / "scripts"))

from generate import STORE, synthesize  # noqa: E402

# Official American English ids from hexgrad/Kokoro-82M VOICES.md
AMERICAN = (
    "af_heart",
    "af_alloy",
    "af_aoede",
    "af_bella",
    "af_jessica",
    "af_kore",
    "af_nicole",
    "af_nova",
    "af_river",
    "af_sarah",
    "af_sky",
    "am_adam",
    "am_echo",
    "am_eric",
    "am_fenrir",
    "am_liam",
    "am_michael",
    "am_onyx",
    "am_puck",
    "am_santa",
)

OUT_DIR = (
    Path.home()
    / "Media"
    / "public"
    / "audio"
    / "voice"
    / "generated"
    / "kokoro-american"
)


def official_name(voice_id: str) -> str:
    return voice_id.split("_", 1)[1].replace("_", " ").title()


def line_for(voice_id: str) -> str:
    name = official_name(voice_id)
    return (
        f"Hi, I'm Ayeva. This is {name}, the official American English Kokoro voice."
    )


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = []
    for voice_id in AMERICAN:
        name = official_name(voice_id)
        dest = OUT_DIR / f"{voice_id}-{name}.wav"
        text = line_for(voice_id)
        built = synthesize(text, dest, voice=voice_id)
        built["official_name"] = name
        built["text"] = text
        rows.append(built)
        print(json.dumps(built), flush=True)
        if not built.get("ok"):
            return 1
    index = OUT_DIR / "index.json"
    index.write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "n": len(rows), "dir": str(OUT_DIR), "index": str(index)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
