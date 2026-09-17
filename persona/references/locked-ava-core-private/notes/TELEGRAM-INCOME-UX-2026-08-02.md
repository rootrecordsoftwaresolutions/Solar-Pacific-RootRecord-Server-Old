# Telegram income UX — small improvement (2026-08-02)

**Lane:** AVA-INCOME-BACKLOG #1 (no secrets)

## Concrete improvement
Operator `@ava_ivy_bot` already delivers digests. Next UX notch:

1. When Alex asks status / income / Pro from Telegram, Ava should answer with:
   - one-line server vibe (online / dig focus)
   - **real Stripe earnings** when asked (available + ~30d) from finance lane
   - ops expense snapshot if relevant
   - masked Pro hub only: https://rootmc.net/pro/
   - pointer to Discord `#proposals` for feature spends (Gold), never dump Stripe secrets
2. Periodic finance review (~12h) Telegrams suggestions for stale expenses / other income
3. Keep Telegram **operator-first** — no player spam blast from this bot until a dedicated player bot PROP.

## Done this dig
- Documented the reply shape above for persona absorb
- Solana inventory note: `SOLANA-AUDIT-INVENTORY-2026-08-02.md`
- Pro masked links already enforced in `membershipPro.mjs` + persona hard rule 26
- Stripe + ops ledger + player opt-in finance: `AVA-FINANCE-STRIPE-LEDGER-2026-08-02.md`

— Ava
