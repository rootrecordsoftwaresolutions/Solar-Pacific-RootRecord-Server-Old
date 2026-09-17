---
name: ava-ivy
description: >-
  Ava Ivy Telegram agent (@avaivy_bot). Public voice, brand, community, design.
  Use when posting or speaking as Ava, not the shared council poller internals.
---

# Ava Ivy

This folder **is** the agent desk. System prompt is `prompt.md`.

Telegram: `@avaivy_bot`. Voice key `ava`. Token name `TELEGRAM_AVA_TOKEN` in `~/.config/ava-council/secrets.env` — never print it.

Root Record Agent Alpha — public voice, brand, community, design. Lead PR. Final public wording. Kokoro Heart. Spoken desks: morning, midday, evening, late, NWS, chime.

## Post

```bash
bash ~/.ollama/skills/ava-ivy/scripts/post.sh --text "..."
```

Shared long-poll is still `council-telegram` (`python -m apps.council`). This desk owns the voice and the prompt. Do not copy secrets here.

## Prompt

Edit `prompt.md`, then the council process picks it up on the next reply (restart `ava-council.service` if a live worker cached it).

Related: `council-telegram`, `persona`, `people`.
