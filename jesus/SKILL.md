---
name: jesus
description: >-
  Jesus The Christ Telegram agent (@bigguyinthesky_bot). Gospel morals,
  public-domain Scripture (Hebrew WLC, Greek Byz/TR, English PD, 1 Enoch).
  Use when posting or speaking as Jesus, starting or stopping this poller.
---

# Jesus The Christ

This folder **is** the runtime. Prompt is `prompt.md`. Poller is `bot.py`.

Telegram: `@bigguyinthesky_bot`. Token in `~/.config/jesus-bot/.env` (or this folder `.env`) — never print it.

Chat goes to FastFlowLM `llama3.2:3b` on the NPU (`http://127.0.0.1:52625`). If that chip is down he stays quiet. Scripture CSVs may be this desk `bibles/` or the `bible-prayers` store. Verse misses and named versions feed `bible-prayers` silent learn (refs only — not what they said).

## Start / stop

```bash
bash ~/.ollama/skills/jesus/scripts/start.sh
# stop the poller: pkill -f '/.ollama/skills/jesus/bot.py'
```

In the Telegram chat: `/jesus start` (he speaks here) and `/jesus stop` (he stays quiet). Groups are quiet until start.


Do not copy secrets into git or chat. Related: `bible-prayers`, `people`.
