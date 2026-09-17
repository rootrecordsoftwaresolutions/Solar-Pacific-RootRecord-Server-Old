---
name: kokoro
description: >-
  Local Kokoro-82M TTS. All spoken generation writes WAV.
---

# kokoro

This folder **is** the runtime. Do not invent watts.

Hexgrad [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) on this PC. Origin (Python 3.14) shells out to `store/venv` (CPython 3.12 + Torch CPU). Output is always 16-bit PCM **WAV**.

Clip-stitch TTS is off: `~/.ollama/disabled/clip-tts`.

## Pull / generate

```bash
~/.ollama/skills/kokoro/store/venv/bin/python \
  ~/.ollama/skills/kokoro/scripts/pull_model.py
~/.ollama/skills/kokoro/store/venv/bin/python \
  ~/.ollama/skills/kokoro/scripts/generate.py \
  --text "Aloha from Mountain View." \
  --out /tmp/kokoro-check.wav
```

Locked speakers: Ava Heart (`af_heart`), Bruce Echo (`am_echo`), Carly Nova (`af_nova`). Automated desks route through `scripts/speakers.py`. Do not write WAV unless the body has live facts.

Topic index: `reports-voice`.
