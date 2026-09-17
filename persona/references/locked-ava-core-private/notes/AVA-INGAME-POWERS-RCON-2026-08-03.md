# Ava in-game help powers (2026-08-03)

Alex on PROP thread (`1533887591089438821` / bonus `1533886704392474806`): help in-game as much as possible + unlock powers via RCON. Litematica place noted; WorldEdit offered.

## What shipped

- `rconGuard.mjs` — `avaBuildAssist: true` unlocks console `setblock` / `fill` / `clone` / `execute` / WE-`//*` / schem / `dh` / `rootspawn` (still blocks bare `op`/`stop`)
- `avaIngamePowers.mjs` — `enableAvaIngamePowers()` probes Claims+Towny + arms path
- No Mojang profile **AvaIvy** → cannot player-op; powers = **console RCON**
- Root-Perms group **`ava`** already exists — attach when a real account joins (`rootperms user <name> addgroup ava` while online)

## Needs from Alex

1. Drop **WorldEdit** or **FAWE** jar on Claims (+ Towny if wanted) — Litematica place is mostly client; server paste needs WE
2. Optional: real Mojang alt for Ava body + `rootperms user … addgroup ava`

## PROP package

Still staged in FileZilla handoffs — Shockbyte upload/restart still the live gate.
