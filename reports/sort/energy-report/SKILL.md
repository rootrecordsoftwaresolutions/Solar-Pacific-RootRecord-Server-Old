---
name: energy-report
description: >-
  Carly energy desk: latest Solar Panels still + pack facts + ops vision read.
  Use for energy report, solar cam attach, or site security energy watch.
  Does not cycle River car DC.
---

# energy-report

Carly posts the **energy desk** — Solar Panels cam plus live pack lines. She already knows the wood-frame array and evening upright stow; speak ops deltas only.

- Uses the **most recent** `panels-cam` still (no power cycle).
- Vision returns rain / angle / glare deltas — not tourist scenery.
- Pack SOC / PV from BLE quota files only — no invented watts.
- **Kokoro voice** as Carly (`af_nova`) — WAV attached with the Telegram post.
- River car 12V left alone here. `panels-cam` owns the 15‑min grab cycle.

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/energy-report/scripts/energy_report.py --post
```

`--no-voice` skips WAV. Audio: `store/audio/energy-current.wav`
