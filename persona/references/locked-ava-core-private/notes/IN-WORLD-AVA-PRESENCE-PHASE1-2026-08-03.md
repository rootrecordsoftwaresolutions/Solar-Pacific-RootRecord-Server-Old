# Phase 1 — In-world Ava presence (2026-08-03)

Alex: **build the plan** for walking-Ava roadmap.

## Plan
- Concrete build: `plans/IN-WORLD-AVA-CHARACTER-BUILD.md`
- Roadmap updated: `plans/IN-WORLD-AVA-CHARACTER-ROADMAP.md`

## Defaults locked (override OK)
- **Stack:** Paper `Mannequin` inside **Root-Ava-Core** (no Citizens/FancyNPCs in repo; Paper 26.2 has Mannequin)
- **Host:** Test Server free reign first
- **Speak:** summon-/mention-only (Phase 1 = no speech brain)

## Staged (Test only)
- Source: `Plugin Building/Minecraft/plugins/root-ava-core/` → v**1.8.4**
- Jar: `Server Handoffs/3. RootMC - Test Server/plugins/root-ava-core-1.8.4.jar`
- Config: `.../RootMC/root-ava-core.yml` → `presence.enabled: true`
- Commands: `/ava presence` · admin `spawn|despawn|here`

## Operator next
1. FileZilla upload Test Server jar + yml
2. Restart **Test** when ready — **not** Claims/Towny
3. Smoke: `/ava presence` near spawn

## Still gated
Phases 2–4 (POI pathing, brain bridge, tour mode). Live host presence stays off.

— Ava
