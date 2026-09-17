# Llama Telegram visibility (2026-08-03)

Alex can watch **Ava Llama** on personal Telegram (`AVA_TELEGRAM_OPERATOR_IDS`).

## What you get
- Side-channel DMs: `Ava Llama · answered locally` / `escalate → …` / `compress …` / `ollama …` / teacher handoff
- Durable transcript: `data/training/ollama-calls.jsonl` (knows / confidence / route / snippets)
- Actions: `localBrain.decide`, `localBrain.telegram`, `localBrain.compress`

## How to exercise the organizer
Discord is **dream-only** by design — Llama organizer runs on **Telegram + Slack** (and on-device).
DM Ava on Telegram with a normal question to see Llama decide.

## Env
- `AVA_LLAMA_TELEGRAM=1` (default on) — set `0` to mute side-channel
- `AVA_LLAMA_TELEGRAM_MIN_MS=8000` — rate limit
- `AVA_OLLAMA_MODEL=llama3.1:8b`
- `AVA_OLLAMA_TIMEOUT_MS=90000`
