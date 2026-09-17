# Root-Claims

## 2026-07-27 â€” Configurable metrics

- Optional `economy.expansion-prices-g` list (overrides baseÃ—multiplier).
- `timings.confirm-ttl-seconds`, `timings.spawn-cooldown-seconds`.
- `limits.require-prior-areas-at-max` toggle.

## 2026-07-27 â€” `/c deposit all`

- `/c deposit <amount|all>` (alias of `/c bank deposit`) deposits wallet Gold into the current area bank.
- `all` moves the full wallet balance; empty wallet gets a clear message.

## 2026-07-27 â€” Territory mobs

- Hostile mobs may spawn/live in the **territory band**; no-mob claims only block spawn/entry **inside the circle**.
- Targeting a player in a no-mob claim no longer deletes territory mobs (aggro cancelled only).

## 2026-07-23 â€” Areas reform

- **Areas** replace flat claim caps: max **3 areas** per player, each level **1â€“10** (circles).
- Founding (`/c new [name]`): **75 / 1000 / 10000 G** from wallet; slot 2+ requires prior areas at level 10.
- Expansion (`/c claim`): **75 Ã— 1.5^(levelâˆ’1) G** from that **area bank**; owner-only.
- Auto names `username(1..3)`; display name stored on area root.
- Members: `/c add|trust|friend` (same list); unlimited memberships elsewhere.
- Unclaim refund **50%**; cannot unclaim a circle that still has expansions.
- `/spawn <player|area> [area]` visits area spawn (Root-Essentials â†” Root-Claims); tab-complete for players and area names; `/spawn` alone = server spawn.
- Player wiki: https://rootmc.net/wiki/claims/
- Config: `Plugin Building/Minecraft/plugins/root-claims/.../root-claims.yml` (+ handoff copy).
- Plan: `Plugin Building/Minecraft/docs/ROOT-CLAIMS-REFORM-PLAN.md`

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
