# Incident — Ava Ivy Discord spaz (2026-07-31)

**Where:** RootMC Discord `#general` (`1516108586307158088`)  
**Status at write-up:** Bot left offline while anti-spaz + persona fixes landed  
**Severity:** Chat spam / UX only (no economy or live server damage)

## What happened

Ava flooded `#general` with near-duplicate replies (backend/status chatter repeated), kept talking after “Stop Ava,” and felt chaotic.

## Root cause

1. Restart wiped in-memory `seen` → poller re-answered old pings
2. Loose bare-name triggers
3. No real hush state
4. No ack — long work dumped multiple full essays

## Fix direction (runtime)

- Boot = one offline summary + “sorry I was asleep / I’m active”
- Persisted seen / watermarks / hush under `data/`
- Tighter triggers; ack-then-one-answer
- Public Discord never names other AI products (say Root Server)
- Drop zone: this folder’s `uploads/` + `plans/`

## Slack

Posted to `#all-rootmc` (RootMC Slack workspace) on 2026-07-31.
