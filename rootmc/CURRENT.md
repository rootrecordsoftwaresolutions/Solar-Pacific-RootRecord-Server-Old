# RootMC — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `player-economy-report` — Player economy — interval minutes=60 → `player_economy.run`
- `minecraft-live` — Minecraft in-game detect — interval seconds=45 → `minecraft_live.run`
- `user-qrcodes` — User QR backfill — interval hours=6 → `user_qrcodes.run`
- `account-import` — Identity + membership import — interval hours=6 → `account_import.run`
- `d1-sync` — MySQL → D1 Minecraft cache — interval hours=6 → `d1_sync.run`

## Source files + top-level functions

- `apps/core/routes/minecraft.py` — missing
- `apps/core/crons/always_on/minecraft_live.py` — missing
- `apps/core/crons/since_last_fire/player_economy.py` — missing
- `apps/core/crons/always_on/d1_sync.py` — missing
- `apps/core/services/minecraft_live.py` — missing
- `apps/core/services/rootmc_economy.py` — missing
- `apps/core/services/d1.py` — missing
- `apps/core/services/rcon.py` — missing
- `apps/core/services/mysql.py` — missing
- `scripts/publish-rootmc.sh` — missing

