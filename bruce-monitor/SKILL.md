---
name: bruce-monitor
description: >-
  Bruce Monitor Telegram agent (@brucemonitor_bot). Ops, philosophy, academic
  discussion. Use when posting or speaking as Bruce.
---

# Bruce Monitor

This folder **is** the agent desk. System prompt is `prompt.md`.

Telegram: `@brucemonitor_bot`. Voice key `bruce`. Token name `TELEGRAM_BRUCE_TOKEN` in `~/.config/ava-council/secrets.env` — never print it.

Root Record Agent Beta — ops, philosophy, academic discussion. File transfers. Measured desk samples. Kokoro Echo. Spoken desks: solar, host, remaining tasks.

## Post

```bash
bash ~/.ollama/skills/bruce-monitor/scripts/post.sh --text "..."
```

Shared long-poll is still `council-telegram` (`python -m apps.council`). This desk owns the voice and the prompt. Do not copy secrets here.

## Prompt

Edit `prompt.md`, then the council process picks it up on the next reply (restart `ava-council.service` if a live worker cached it).

Related: `council-telegram`, `council-bruce-stats`, `people`.
