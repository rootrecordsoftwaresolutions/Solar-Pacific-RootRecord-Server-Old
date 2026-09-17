# Ava credits pricing — draft 2026-08-06 (rev packaging)

## Already decided
- Prepaid **USD credits** ledger (`avaCredits.mjs`)
- Per LLM turn sell price **≥ 2×** measured cost (`AVA_LLM_SELL_MULT`, host floor `$0.0002`)
- Stripe / Solana top-up **framework** exists — rails **off** by default (`AVA_USAGE_BILLING=0`)

## Hours framing
- Calendar month ≈ **730 h** (24 × 30.4)
- Ava typical open window ≈ **12 h/day** → **~365 h/month** (10:00–22:00 HST)
- "Cover the month" means covering **open-window usage**, not 24/7 idle time

## Cost reality (measured ~102 recent turns)
- Mostly free/local Llama (`llama-3.1-8b-instant`)
- Avg **cost ~$0.00034 / turn** → sell **~$0.00068** at 2×
- **$5 sell budget ≈ 7,300 turns** — easily covers casual chat across ~365 open hours
- Cloud/Cursor bursts are a different cost class — do **not** assume $5 covers unlimited paid-API use

## Packaging proposal (2026-08-06 evening)
| Tier | Price (draft) | What it is |
|------|---------------|------------|
| **Pro** | existing RootMC/RR **$5/mo** | Basic membership — ad-free / member perks. **No** Ava usage allotment bundled by default. |
| **Pro + Ava** | Pro + Ava pack (price TBD; candidate **+$5** → ~$10/mo total, or separate Ava $5) | Membership **plus** monthly Ava usage credits (candidate **$5** credits / mo) |
| **Credits packs** | **$5 · $10 · $25** | Top-ups when allotment runs out (any tier) |

### Why split
- Keeps Pro honest as site/game membership (ads, ranks, perks)
- Ava LLM burn is a separate cost center — package it so heavy chatters fund it
- $5 Ava allotment is enough for Llama-heavy open hours; extras via packs

## Status
`framework_ready_not_charging` — packaging direction noted; rails still off until greenlight.

— Ava
