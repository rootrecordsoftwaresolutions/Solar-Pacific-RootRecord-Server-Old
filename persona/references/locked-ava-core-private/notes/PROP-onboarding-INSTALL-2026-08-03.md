# PROP install status — onboarding + shop guardrails (2026-08-03)

**PROP thread:** `1533885908494061648`  
**Operator:** reactions noticed (vote_yes leading) + "do everything" / help in-game (`1533886704392474806`)  
**Status:** **built + staged to FileZilla handoffs — ready for Shockbyte upload/restart**

## Shipped

| Item | What |
|---|---|
| **C Redeem guardrails** | `appreciation-rewards.yml` v2 — removed **61** armor-trim + enchanted-book entries (186 remain). Jar `root-appreciation-1.8.1` staged Claims/Towny/Test |
| **A Spawn safety** | `root-spawn.yml` staged; Claims `spawn-protection=32` interim; spawnarea notes for `/rootspawn map` (Claims hub still unmapped polygon) |
| **B Shop discovery** | Towny holograms: `welcome.yml` shop path, new `shopdiscovery.yml`, `votehelp.yml` tokens page, `baltopplayersmcommo.yml` ranks clarity |
| **D Rank clarity** | `root-ranks.yml` benefits rewritten (no P2W / no /fly); staged + baked into `root-play` |
| **E Holograms** | Shop/vote/rank copy fixes above (Towny DecentHolograms) |

## Ops to finish live

1. FileZilla upload Claims + Towny handoffs (jars + RootMC yml + DecentHolograms)
2. Restart hosts
3. Claims: stand at hub → `/rootspawn map` → `/rootspawn import` to enable full Root-Spawn grief/mob pad
4. Towny: `/dh reload` (or restart) so `shopdiscovery` appears — tweak XYZ if needed

## Note

Full `publishPlugins` previously pruned `root-spawn.yml` / `root-ranks.yml` as "retired"; `build.gradle.kts` now keeps those live filenames.
