# PROP — New-player onboarding + shop economy guardrails

**Source:** player feedback from **hihihi6702** in #general (2026-08-03), opened by operator request.  
**Author:** Ava Ivy (lead-dev)  
**Status:** IMPLEMENTING — jars/configs staged 2026-08-03 (see `PROP-onboarding-INSTALL-2026-08-03.md`)

## Problem

1. **/vote and /bonus** appear to grant rare **armor trims** and **enhanced books** that player shops are built around — undercuts shop grind.
2. **Shop discovery** is weak for new players (no clear spawn → shops / warps / AH path).
3. **Spawn** has been reported as diggable / unprotected / open to mobs — bad first impression and unsafe.
4. **Spawn holograms** have intermittent bugs (bug dig; listed for visibility).
5. **Ranks** feel unclear vs announcer OP commands — players don't see why `/rankup` is worth it. (No P2W, no `/fly` as endgame — locked values.)

## Plan (if this PROP passes)

### A. Spawn safety (priority)
- Verify Claims / protection / mob spawn rules at spawn.
- Make spawn **safe**: protected from grief digs, no hostile mob spawns in the core spawn pad.
- Document the protection layer in staff notes.

### B. Shop discovery
- Add a clear **spawn → shops** signal (hologram and/or warp board and/or short announcer tip).
- Ensure `/warps` / AH / shop path is obvious within the first minute at spawn.
- No forced teleport tax; discovery only.

### C. Vote / bonus reward guardrails
- Audit live `/vote` and `/bonus` reward tables.
- **Do not free-mint** the same rare armor trims / shop-competitive enchanted books that player markets stock.
- Prefer cosmetics, consumables, modest Gold (G), or clearly common mats — keep rare trims/books in the shop/grind lane unless a later PROP says otherwise.
- Publish the post-audit table in #updates (or changelog) when changed.

### D. Ranks clarity (no P2W / no /fly)
- Inventory what each rank actually unlocks today.
- Align chat announcer "new command" callouts with rank gates where appropriate.
- Publish a short player-facing rank perk list (wiki or spawn board).
- **Hard locks unchanged:** no pay-to-win combat/loot power; no `/fly` as a default rank crown.

### E. Holograms (bug track)
- Reproduce + fix buggy spawn holograms (verify-then-fix; not gated on this vote if clearly broken).

## Out of scope
- Redesigning the whole economy in one pass
- Adding `/fly` or P2W perks
- Forcing players to build bases

## Risks
- Vote reward nerfs may temporarily lower vote motivation — mitigate with clear Gold (G) / cosmetic alternatives.
- Spawn protection misconfig could block legitimate build plots nearby — keep pad tight.

## Rollback
- Revert reward table / hologram / warp board to pre-change configs from handoff backup.
- Spawn protection: restore prior region flags from staff backup.

## Success
- New players can find shops from spawn in under a minute.
- Spawn is safe and not diggable in the core pad.
- Vote/bonus no longer dumps shop-competitive rare trims/books.
- Rank perks are readable; no P2W / no /fly creep.
