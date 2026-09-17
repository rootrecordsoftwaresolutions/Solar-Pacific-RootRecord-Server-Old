# Channel scan fix — dream-dark spam (2026-08-03)

Alex: "scan all channels and fix ava" after Ava flooded #general with identical "Root Server and dream state are both dark…" then answered "chill tf out" with `mm?`.

## Root cause

Cursor/dream both dark → `localRecommend` always returned the same public stall. Pipeline then stacked more stalls + "skipping the redo" on near-duplicates.

## Shipped

- `localBrain.mjs` — per-channel dark-stall cooldown (~15m); second hit gets a short "already queued / no more spam" line
- `recommend.mjs` — pass `channelId` into local brain
- `pipeline.mjs` — if near-dupe is a dark stall / skip-redo, **react-only** (no more skip-redo spam)
- `classify.mjs` — chill/stop-spam soft reply (not `mm?`)

## Public catch-up (bot token)

- #general: chill ack · side projects named · map/chunk · Zuppa 28d
- #updates: responses caught-up ack

## Restart

`npm run restart` after this note.
