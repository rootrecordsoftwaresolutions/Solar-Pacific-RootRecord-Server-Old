# RootMC config templates

Non-secret configs synced from the Jun 2026 Paper backup. Copy onto the live host under the matching plugin folders; keep **MySQL passwords** and API keys on the server only (never commit).

## Shockbyte MySQL (shared)

| Field | Value |
|-------|--------|
| Host | `mysql.shockbyte.hil2.shockbyte.host` |
| Port | `3306` |
| Database | `75eedc3b19-rootmc` |
| Username | `75eedc3b19-rootmc-admin` |
| Password | Shockbyte panel — set in live configs only |

**Plugins using this DB:** Towny (`towny_*`), mcMMO (`mcmmo_*`), RootMC (`root_*` cache tables), QuickShop (legacy, if still installed).

**Data flow:** MySQL on Shockbyte → RootMC plugin (reads mcMMO/playtime, writes link + shop cache) → `POST` sync every 5 min → Cloudflare D1 (app, daily report, Discord). Workers do not connect to MySQL directly.

| Template | Live path |
|----------|-----------|
| `Essentials/kits.yml` | `plugins/Essentials/kits.yml` |
| `Essentials/worth.yml` | `plugins/Essentials/worth.yml` |
| `Essentials/config-economy-snippet.yml` | Merge into `plugins/Essentials/config.yml` |
| `Towny/economy-no-daily-tax-snippet.yml` | Merge into `plugins/Towny/settings/config.yml` — **no daily taxes** |
| `Towny/config.yml` | Full Towny settings synced from `backup06102026` (daily taxes disabled) |
| `Towny/database.yml` | `plugins/Towny/settings/database.yml` — set `sql.password` on host |
| `Towny/worlds/worlds.txt` | `plugins/Towny/data/worlds.txt` — register overworld + nether + end |
| `Towny/worlds/world.txt` | `plugins/Towny/data/worlds/world.txt` — **claimable**, wilderness build on |
| `Towny/worlds/world_nether.txt` | `plugins/Towny/data/worlds/world_nether.txt` — **not claimable**, wilderness build on |
| `Towny/worlds/world_the_end.txt` | `plugins/Towny/data/worlds/world_the_end.txt` — **not claimable**, wilderness build on |
| `../host-handoff/config-templates/RootMC/rootmc.yml` | `plugins/RootMC/rootmc.yml` — set `mysql.password` on host |
| `../host-handoff/config-templates/RootMC/rootmc-shops.yml` | `plugins/RootMC/rootmc-shops.yml` |
| `../host-handoff/config-templates/RootMC/roothelp.yml` | `plugins/RootMC/roothelp.yml` — rules, command list, Discord URLs (optional; jar seeds defaults) |
| `../host-handoff/config-templates/RootMC/root-essentials.yml` | `plugins/RootMC/root-essentials.yml` — economy/homes/sell config for Root-Essentials |
| `../host-handoff/config-templates/RootMC/root-rewards.yml` | `plugins/RootMC/root-rewards.yml` — playtime milestones + vote gold (set `mysql.password` on host) |
| `../host-handoff/config-templates/RootMC/root-announcer.yml` | `plugins/RootMC/root-announcer.yml` — rotating broadcast messages |
| `../host-handoff/config-templates/RootMC/cloud.yml` | `plugins/RootMC/cloud.yml` — set `server-id` + `server-secret` on host |
| `../host-handoff/config-templates/luckperms-setup.commands` | Run in console to apply live LP model: `default -> pro -> lifetime` + `admin` (non-OP) |

Repo plugin defaults: `Minecraft/plugins/rootmc/src/main/resources/rootmc.yml` and `Minecraft/plugins/rootmc-shops/src/main/resources/rootmc-shops.yml`.

Server identity: **RootMC** (`server_id: rootmc`). Connection address and BlueMap URL are set on the live host only — hidden from public wiki/API until launch.

**Towny world rules (RootMC):** Wilderness build/break is on in all dimensions. Only overworld (`world`, from `server.properties` `level-name`) allows `/town new` and `/t claim`. Nether/end are not claimable.

**MySQL hosts:** Towny loads world flags from `TOWNY_WORLDS`, not only `plugins/Towny/data/worlds/*.txt`. After copying templates, run on the Shockbyte DB (or in-game: `/townyworld toggle wildernessuse` per world, then claimable toggles):

```sql
UPDATE TOWNY_WORLDS SET unclaimedZoneBuild=1, unclaimedZoneDestroy=1, unclaimedZoneSwitch=1, unclaimedZoneItemUse=1
WHERE name IN ('world','world_nether','world_the_end');
UPDATE TOWNY_WORLDS SET claimable=0 WHERE name IN ('world_nether','world_the_end');
```

Also set `unclaimed_zone_build/destroy/switch/item_use` to `'true'` in `plugins/Towny/settings/config.yml`, then `/towny reload` or restart.

**BlueMap:** **Overworld only**, **flat view** (perspective + free-flight off). Render-mask **±10k** + world-border marker. `bluemap-overworld-only.commands` after config upload. Expand from ±5k: `resize-to-10k.commands`.

**Full map without exploration:** Install **Chunky** (`config-templates/Chunky/config.yml` — `continue-on-restart: true`). Run `chunky-setup.commands`. BlueMap tiles → R2: `Web/cloudflare/rootrecord-minecraft-map/BLUEMAP-R2.md`.

**Public map URL:** `https://map.rootrecord.info/` — Worker `rootrecord-minecraft-map` serves **R2** (`rootrecord-bluemap`) with live proxy to `:22784`. Setup: `Web/cloudflare/rootrecord-minecraft-map/BLUEMAP-R2.md`. Templates: `BlueMap/webapp.conf` (`live-data-root`), `webserver.conf`, `core.conf`, `plugin.conf`.
