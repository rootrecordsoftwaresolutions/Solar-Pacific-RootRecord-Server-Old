# Ava token / Gold credits / server reserve

**Updated:** 2026-08-02 (Wave 5)

## Surfaces & cost board

| Surface | Meter | Notes |
|---|---|---|
| Cursor Root Server | agent tokens / digs | Staff Slack / on-device only |
| Dream brain | cloud replies | Discord default dream path |
| Discord | bot API + rate limits | Multipost ~1900 chars |
| Slack | bot API + rate limits | Multipost ~3800 chars |
| Telegram | operator master ops | Not a player spend surface |

Status panel: `http://127.0.0.1:8787/` → Token board section (from `tokenEconomy.mjs` snapshot).

## Soft rate limits

| Tier | Ceiling (soft) |
|---|---|
| Free player digs (Discord) | modest; chill when sweater / overload |
| Membership ~$5/mo | ~$3 equivalent usage soft ceiling unless more credit purchased |
| Operator (Alex) | uncapped for ops; still respect Discord/Slack API limits |

## Gold as Ava credits

- Player-facing currency remains **Gold (G)**.
- Ava credits map to Gold for in-ecosystem usage (not USD display in player copy).
- Automated payouts stay **treasury debit**, never wallet mint.

## Server reserve (Ava-owned logic)

1. Each host has an isolated reserve ledger (Claims ≠ Towny).
2. Compare hosts only when an operator explicitly asks.
3. If reserve ledger &lt; 0 → pause automated payouts for that host.
4. Bond/reserve watch stays on urgent registry until ledger healthy after 1.8.0.

## Enforcement hooks (v1)

- Config: `Server Handoffs/Ava Ivy/data/token-economy.json`
- Module: `Web Files/rootmc-ava/src/tokenEconomy.mjs`
- Pipeline may soft-refuse non-ops digs when over soft cap (stub OK if board + config live).
