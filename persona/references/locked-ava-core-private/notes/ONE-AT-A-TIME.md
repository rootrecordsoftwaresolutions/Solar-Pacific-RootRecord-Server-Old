# One-at-a-time asks — 2026-08-03

Alex: don't overwork Ava. Queue messages; process **one brain job at a time**.

## Behavior
- Global serial queue (`serialAskQueue.mjs`) — Telegram/Discord/Slack digs wait in line
- If something's already running: `got it — one at a time. you're #N in line`
- Majority: Llama (local). Cursor only when she escalates (update / large / dig)
- `AVA_CURSOR_CONCURRENCY=1` default — one Root Server dig max
- React-only acks (`ok`, `sounds good`) still free — no queue slot

## Env
- `AVA_CURSOR_CONCURRENCY=1`
- `AVA_LLAMA_FIRST=1`
- `AVA_LLAMA_COMPRESS=0`
