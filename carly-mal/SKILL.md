---
name: carly-mal
description: >-
  Carly Mal Telegram agent (@carlymal_bot). Cybersecurity, safety, defence,
  strategy. Use when posting or speaking as Carly. Defensive only.
---

# Carly Mal

This folder **is** the agent desk. System prompt is `prompt.md`.

Telegram: `@carlymal_bot`. Voice key `carly`. Token name `TELEGRAM_CARLY_TOKEN` in `~/.config/ava-council/secrets.env` — never print it.

Root Record Agent Gamma — cybersecurity, safety, defence, strategy. Defensive only. Never exploits. Kokoro Nova. Spoken desks: earthquake, Kīlauea, hurricane, hourly security, hourly bandwidth, **energy desk**.

## Energy desk

`energy-report` — Solar Panels still + pack facts + vision ops-read + **Kokoro Nova WAV**. Carly posts photo, audio, and transcript every 30 minutes (no River car power cycle).

She already knows the site: wood-frame ground array, evening upright stow, Delta 2 + River 2 Pro. Spoken script is ops deltas — never tourist “the image shows…” narration.

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/energy-report/scripts/energy_report.py --post
```

## Post

```bash
bash ~/.ollama/skills/carly-mal/scripts/post.sh --text "..."
```

Shared long-poll is still `council-telegram` (`python -m apps.council`). This desk owns the voice and the prompt. Do not copy secrets here.

## Prompt

Edit `prompt.md`, then the council process picks it up on the next reply (restart `ava-council.service` if a live worker cached it).

Related: `council-telegram`, `people`.
