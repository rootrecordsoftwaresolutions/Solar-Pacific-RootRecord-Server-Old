# RootMC Day 1 launch checklist

## Before IP goes live

- [ ] Seed chosen and `level-seed` set in `server.properties`; fresh `world/` generated
- [ ] Staff in LuckPerms `admin` group only — **not** in `ops.json` (see `ROOTMC-OPS.md`)
- [ ] `plugins/RootMC/cloud.yml` secrets set; `rootmc.yml` + `rootmc-shops.yml` deployed
- [ ] Essentials starter kit + `worth.yml` on host
- [ ] Spawn admin shop plots built; listings use **rootmc-shops** (QuickShop removed after cutover)
- [x] Towny config merged — `economy-no-daily-tax-snippet.yml` / `config-templates/Towny/config.yml` (no daily upkeep)
- [ ] Towny nation hub near 0,0 (Intelligenz)
- [ ] D1 migration `0087_rootmc_ingame_vault_market.sql` applied on account DB
- [ ] Plugin jars published to `Web/main/realm/plugins/` and Pages deployed

## Smoke tests (each admin)

- [ ] `/rootmc link` → verify at rootmc.net/verify → app shows stats
- [ ] `/rootmc waypoint` and `/rootmc note` → sync app → waypoints with **Recorded in-game**
- [ ] Create shop (chest + sign), price cap rejects over-limit listing
- [ ] `/buy <item>` in-game; app **Stock market** buy → `/vault` claim
- [ ] BlueMap URL loads in RootMC app tab
- [ ] McMMO, playtime, net worth visible after sync cycle (~5 min)

## Fresh start

- Reset playerdata / new world so no admin has a head start
- Everyone joins together as new players
