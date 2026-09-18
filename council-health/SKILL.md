---
name: council-health
description: >-
  Periodic check that Ava, Bruce, and Carly can talk: council process, Telegram
  getMe, FastFlowLM chat, origin health. Alerts the group when something is down.
---

# council-health

Every 5 minutes (scheduler) verifies the council stack while AVA Console is up:

- Exactly one `apps.council` process
- Telegram `getMe` for Ava / Bruce / Carly
- FastFlowLM (`:52625`) models + short chat probe (everyday voices)
- Origin `:8787` health
- Recent `ava-council.log` for getUpdates 409 conflicts

Ollama GGUF may be down on purpose — chat uses the NPU. Failures alert the
council group (30‑minute cooldown). Does not start origin/Ollama/FLM when the
console is closed.

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/council-health/scripts/council_health.py
```

`--no-alert` / `--no-probe` / `--json` available.
