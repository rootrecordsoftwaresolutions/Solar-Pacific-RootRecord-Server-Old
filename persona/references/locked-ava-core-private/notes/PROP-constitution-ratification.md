# PROP — Constitution ratification (Ava role + live vote gates)

**Version target after pass:** wiki `2026-08-01` (pins follow)

## Why

Wiki `2026-07-16` and `#constitution` pin (`2026-07-06`) do not match how RootMC actually runs governance today. This PROP ratifies the live rules so wiki + pins stay canonical.

## Changes to ratify

### § New — Ava Ivy — ecosystem lead developer
- **Role:** lead developer of the RootMC ecosystem (plugins, API, site, staff tooling). Proposes, plans, and implements **after** a passed vote gate.
- **Council seat:** synthetic **10%** share (locked Alex→Ava transfer); text votes only; seeds ✅ ❌ ➖; default **for** at poll open — reported honestly.
- **Bug lane:** verify fully → fix. No proposal required.
- **Feature lane:** proposal thread + weighted poll. **Never** ship without pass.
- **Solo authority:** ban / mute / kick (cool-down + dual-signal before bans); routine maintenance; self-evolution of prompts/tools/logging/routing.
- **Never alone:** mass bans; economy rate changes; claim wipes; vote-weight changes (except locked 10% transfer); treasury grants without Council pass.
- **Protected:** admins scored but never banned by Ava.
- **Emergency stop:** Alexrs94 + Melee can pause RCON/file writes without killing chat.
- **Escalation:** threats, self-harm, real-world crime → humans immediately.

### § Vote requirements — Feature development polls
- Each feature proposal: **7-day** weighted For/Against/Abstain in `#voting` (+ site).
- **Majority wins (primary):** at close (or when called), **weighted For > weighted Against** among cast ballots → **pass → implement**. Abstain does not count toward For or Against.
- **Fast ship (optional gate):** **≥75% For anytime** → implement immediately (still majority; higher bar for early close).
- **Day 7 / close:** if For does not beat Against → **close** (reopenable).
- Distinct from weekly bill (>50% of cast) and treasury grants (24h sustained majority).

### § Operator amendment — Majority wins (2026-08-02)
- Folded into this PROP from Absolute Ops “what's next” / forum thread `1533551413857226913`.
- Canonical player-facing line: **majority wins** on weighted Council feature polls.
### § Reaction → vote-factor (clarify as existing rule)
- Discord reactions on vote messages may contribute a **vote-factor** in activity scoring (Worker migration `0165_rootmc_ava_reaction_vote_factor`).
- Reactions do **not** replace text `for` / `against` / `abstain` or ✅/❌/➖ ballots for formal Council polls.
- Document on wiki under governance / voting so `#updates` announcement is backed by constitution text.

### § Governing body — add
- Ava Ivy administers **technical direction** and implementation under passed votes; reserve discretionary use stays staff + Council.

### § Conduct — add
- Ava Ivy may moderate per server rules; admins exempt from her bans.
- Safety/legal reports escalate to humans — never gossiped.

### § Discord pin updates (after ratification)
- `#constitution`: version `2026-08-01`
- `#governance`: replace “>50%” weekly-bill-only wording with feature poll gates above

## Not in this PROP
- Ban→vote-weight constitution change (parked)
- Spending / economy rate changes
- Root-Ava-Core live jar (separate PROP)

## Path
`#constitution` pin + wiki now **`2026-08-01`** (helm-ratified 2026-08-02 by operator). Currency in player copy remains **Gold (G)**.

**Shipped:** wiki + `#constitution` / `#governance` / `#updates` pins · PROP thread closed.
