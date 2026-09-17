# Catch-up + clean for Ubuntu — 2026-08-03

## Done
- Canonical home: `E:\.Ava_Ivy` (+ junctions from E/D `Server Handoffs\Ava Ivy`)
- `AVA_HANDOFF` / `AVA_WORKSPACE` on E RootMC `.env`
- Restored nulled `data/urgent-registry.json`
- Catch-up: wake digest + phase-catchup `--all` (soft-acks + 2 followups)
- **Staff vote nag:** pause when D1 listing-vote ingest is globally stale (last vote ~2026-07-23). Do **not** yell at Alex/Melee for ingest failure.
- **Dream 403 failover:** Cursor/Root Server dig when dream returns http_402/403 (keeps DMs useful through Ubuntu prep)
- Operator DM catch-up script: `live/rootmc-ava/scripts/dm-operator-catchup-home.mjs`

## Still open (Ubuntu phase — keep her light)
1. Listing-vote **ingest/sync** into D1 (`rootmc_listing_votes`) — zero rows since Aug 1
2. Dream provider credits (xAI) — optional top-up; failover exists
3. Pit-stop runbook: `notes/PITSTOP-E-UBUNTU-WHILE-D-FLASH.md` · start `E:\PITSTOP-KIT.txt`
4. Plugin `local.properties` still had D: publish paths earlier — point at E `/mnt/e` before wipe

## Fitness rules while cutting over
- One Ava tree only (`SINGLE-AVA-TREE.md`)
- No new sprawl folders — everything under `E:\.Ava_Ivy` + `live/` junctions
- Don't wipe D until pit-stop checklist is green
