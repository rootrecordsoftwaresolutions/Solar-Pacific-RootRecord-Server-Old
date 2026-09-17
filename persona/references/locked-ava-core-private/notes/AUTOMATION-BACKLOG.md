# Ava automation backlog

Grow these so Cursor is not required for every repeat. Check off when owned by Ava runtime/scripts.

| Item | Status | Owner path |
|------|--------|------------|
| Silent archive finished `#proposals` threads | **live** | `scripts/silent-close-props.mjs` |
| Reconcile blocked jobs when Worker already shipped | **partial** | jobQueue markDone + DECISION sync (manual until boot reconcile) |
| Post-ship GitHub push | **live** | `scripts/ava-github-push.mjs` |
| Phase catch-up after digs | **live** | `scripts/phase-catchup.mjs` |
| Quiet in-game chat assist (~3m) | **live** | `src/ingameChatAssist.mjs` |
| vote_yes / vote_no seed on PROP open | **live** | `seedVoteReactions.mjs` |
| Hourly Governance block | **live** | Worker `rootmc-live-economy-status.ts` |
| Absorb Cursor digs → lessons | **live** | notes/LESSONS-* + `recordLocalLesson` |
| Stripe income + ops expense ledger | **live** | `stripeFinance.mjs` + `opsFinanceLedger.mjs` |
| Player finance opt-in (profile-isolated) | **live** | `playerFinance.mjs` |
| Periodic finance review → Telegram | **live** | `financeReview.mjs` (~12h) |
| Ava self-fix (own stack bugs/features) | **live** | `selfFix.mjs` + `cursorSelfFix` |

## Next to automate
1. Job↔Worker reconcile pass on boot (auto markDone when Governance already live)
2. Stronger ingame assist heuristics from training samples
3. Wire silent-close to registry `done` / operator-done flags without Cursor
4. Auto-fill Shockbyte/domain expense rows from invoices when available (still manual-confirm)
5. Self-fix queue from finance-review tooling errors (partial — ask path live)

— Ava
