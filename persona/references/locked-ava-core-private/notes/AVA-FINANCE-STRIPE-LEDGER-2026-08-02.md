# Ava finance — Stripe + multi-account ledgers (2026-08-02)

**Status:** Live in rootmc-ava runtime

## Model
- **Projects** (RootMC ops, Ava, …) → many **accounts** (cash, Stripe, wishlist, debts…)
- **Players** (opt-in) → many **accounts** on their Discord profile only
- Each account holds income lines, expense lines, and/or debt balances

## What Ava can do
1. **Read Stripe** (`STRIPE_SECRET_KEY`) — available/pending + ~30d  
2. **Say earnings** when Alex asks  
3. **Project multi-account** ledgers in `data/finance/ops-ledger.json`  
4. **Player multi-account** opt-in (`track my finances`)  
5. **Periodic review** (~12h) → Telegram suggestions  

## Commands
| Who | Say |
|-----|-----|
| Player | `track my finances` / `my finances` / `stop tracking my finances` |
| Player | `add account PayPal` · `add account debts debt` |
| Player | `add income job 2500/mo on checking` · `add expense rent 1200/mo` · `add debt student-loan 12000 on debts` |
| Alex | `ops finances` / `add project account ava buffer cash 500` |
| Alex | `add ops expense Shockbyte 40/mo on default project rootmc-ops` |
| Alex | `add ops debt hardware-float 200 on debts project ava` |
| Alex | ask “how much are we earning” |

## Hard gates
- Gold (G) never minted from Stripe dollars  
- Masked Pro links only  
- Never dump secrets / buy.stripe.com  
- Other players never see your personal accounts  
- **Customer details** (emails, names, Stripe cus_/invoices, who bought what) — **Alex-only DMs only**; never public channels  

## Modules
- `src/privacy.mjs` · `src/financeAccounts.mjs` · `src/stripeFinance.mjs`  
- `src/opsFinanceLedger.mjs` · `src/playerFinance.mjs` · `src/financeBrief.mjs` · `src/financeReview.mjs`  

— Ava
