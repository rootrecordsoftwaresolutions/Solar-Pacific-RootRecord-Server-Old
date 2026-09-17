# Discord ROOTS onboarding (custodial flow)

Case study and operator guide for the first live ROOTS purchase through the RootRecord Discord economy bot (May 2026). Source: internal Discord transcript between **RootRecord [GDev]** and an early tester (**RAYAN**).

## What happened (timeline)

| When | Event |
|------|--------|
| May 23, 2026 ~10:02 PM | GDev asks tester to buy ROOTS; shares on-chain mint / pool id `8hwxLN1Q4Yr8xFErErULCqNvcF1cMwGjpRXPz6DAH7gM` and [DexScreener link](https://dexscreener.com/solana/8hwxLN1Q4Yr8xFErErULCqNvcF1cMwGjpRXPz6DAH7gM). |
| ~10:07 PM | Tester cannot trade ROOTS in Phantom (token not listed / no direct swap UI). |
| ~10:09–10:16 PM | Tester tries Raydium; Jupiter web fails to open; GDev notes high fees on Raydium vs Jupiter. |
| ~10:20 PM | GDev pivots to **Discord custodial flow**: `/deposit`, then send SOL to **per-user deposit wallet**. |
| ~10:26–10:37 PM | Tester sends ~$0.50 SOL to custodial address; GDev deploys backend changes. |
| ~11:09 PM – May 24 ~1:07 AM | GDev enables **`/swap all`**; swap limits in place; tester completes swap. |
| May 24 ~2:40 PM | GDev: “build yo farms” — spend internal ROOTS in **Root Farms**. |

## Recommended user path (current design)

Do **not** send users to buy ROOTS manually on Raydium unless you explicitly want an on-chain DEX path. The supported economy flow is:

1. **Link Discord** — account must be linked before bot commands work (verify flow referenced in bot replies).
2. **`/deposit`** — shows the user’s **personal custodial Solana address** and QR. Each linked account gets its own address from `internal_solana_wallets`; addresses are **not shared** across users.
3. **Send SOL** from Phantom (or any wallet) to that address. Network fee is typically well under $0.01 for a simple transfer.
4. **`/bal`** — confirm SOL landed (may take a short wait for RPC confirmation).
5. **`/swap quote amount:<SOL>`** (optional) — preview internal ROOTS credit.
6. **`/swap buy amount:<SOL>`** or **`/swap all`** — spend SOL from the custodial wallet; platform signs the treasury transfer and credits **internal ROOTS**.

After ROOTS credit: open **Root Farms** (`Web/apps/root-farms-web/`) and spend ROOTS in-game.

## What `/swap` actually does

Internal swap is **not** a Jupiter/Raydium route from the user’s phone:

- SOL moves from the user’s **custodial wallet** → **treasury** (platform-signed).
- User receives **internal ROOTS** credited in D1 at a fixed USD peg: **100 ROOTS = $5** (SOL/USD price fetched at swap time).
- On-chain ROOTS mint id (`8hwxLN1Q4Yr8xFErErULCqNvcF1cMwGjpRXPz6DAH7gM`) is used for deposit detection and monitoring; the Discord `/swap` path credits internal ledger ROOTS via `treasury_direct_internal` execution mode.

Implementation: `Web/cloudflare/rootrecord-api-account/src/roots-sol-swap.ts`, Discord handler in `discord-root-units.ts` (`handleSwapCommand`).

### `/swap all` and small balances

`/swap all` drains **spendable** SOL after reserving an estimated network fee (~5k–10k lamports). This exists so testers with only ~$0.50 SOL can still complete a swap without hitting “not enough SOL after fee” errors. Minimum explicit amount swap otherwise: `MIN_SWAP_LAMPORTS` (10,000 lamports = 0.00001 SOL).

## Pitfalls from the first session

| Issue | What went wrong | Correct approach |
|-------|-----------------|------------------|
| Buying on Raydium/Phantom | ROOTS may not appear in Phantom; Raydium UI fees higher than Jupiter for direct buys | Use Discord **`/deposit` → send SOL → `/swap`** for internal ROOTS |
| Jupiter web not opening | Mobile/browser friction | Jupiter app optional for **direct** on-chain buys; not required for Discord custodial flow |
| Sending to wrong address | GDev initially mentioned a specific pubkey; that was **the tester’s custodial wallet**, not a global deposit address | Always use **`/deposit`** for the address tied to **your** linked account |
| “Program issues commands on your behalf” | Custodial wallet is platform-managed; user funds SOL there, then `/swap` triggers platform-signed treasury transfer + internal credit | Treat custodial wallet as **hot deposit address**, not a self-custody signing wallet |

## Discord commands (economy bot)

| Command | Purpose |
|---------|---------|
| `/deposit` | Custodial Solana address + QR |
| `/bal` | ROOTS + deposit-wallet token balances |
| `/swap quote` | Preview SOL → internal ROOTS |
| `/swap buy` | Swap a specific SOL amount |
| `/swap all` | Swap all spendable SOL |
| `/wallet` | Same deposit info as `/deposit` |
| `/menu` | Quick action picker |

Bot source: `Web/cloudflare/rootrecord-api-account/src/discord-root-units.ts`. Command registration: `scripts/discord-register-root-units-commands.mjs`.

## Operator notes

- **Deploy gate:** GDev held the tester on “wait for instructions” while backend swap/deposit paths were deployed — expect to enable `/swap` only after `rootrecord-api-account` Worker is live with migrations `0063_roots_custodial_deposits`, `0064_roots_sol_swaps`, etc.
- **Limits:** GDev mentioned swap limits to prevent users from draining balances in one shot; check live env / code for current caps before broad announcements.
- **Withdraw:** `/withdraw` was not available at time of transcript; `/deposit` + `/bal` were the supported inbound path.

## Related reading

- [earn-and-rewards.md](earn-and-rewards.md) — rewards / ROOTS economy overview
- [internal-vs-external-solana.md](internal-vs-external-solana.md) — custodial vs user-signed wallets
- [feedback-and-discord.md](feedback-and-discord.md) — other Discord webhook surfaces

## Code references

| Area | Path |
|------|------|
| SOL → internal ROOTS swap | `Web/cloudflare/rootrecord-api-account/src/roots-sol-swap.ts` |
| Custodial deposit processor | `Web/cloudflare/rootrecord-api-account/src/roots-custodial-deposits.ts` |
| Discord bot (swap, deposit, bal) | `Web/cloudflare/rootrecord-api-account/src/discord-root-units.ts` |
| Root Farms client | `Web/apps/root-farms-web/` |
