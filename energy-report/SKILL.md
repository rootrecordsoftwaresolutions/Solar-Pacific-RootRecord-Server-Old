---
name: energy-report
description: >-
  Carly energy desk: latest Rear Shed panels still + pack facts + vision
  caption. Use for energy report, solar cam attach, or site security energy
  watch. Does not cycle River car DC.
---

# energy-report

Carly posts the **energy desk** — security cam of the solar panels plus live pack lines.

- Uses the **most recent** `panels-cam` still (no power cycle).
- Vision (look model) adds weather/panel context for on-site observations.
- Pack SOC / PV from BLE quota files only — no invented watts.
- River car 12V left alone here. `panels-cam` owns the 15‑min grab cycle.

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/energy-report/scripts/energy_report.py --post
```

Scheduler: `energy-report` every 30 minutes (night-sleep gated). Speaker: Carly.

Latest markdown: `store/energy-latest.md` · frame pointer: `store/LATEST_FRAME.txt`
