# RootMC host handoff (zip and upload)

Built plugin jars and config templates for the live Shockbyte Paper server.

**Live server bundle:** `Server Handoffs\2. RootMC - Towny/` (FileZilla sync + local edits).

**Built:** run from `Minecraft/`:

```powershell
.\build-with-server-jdk.bat publishPlugins
```

Copies jars to `out/`, **`Server Handoffs\2. RootMC - Towny/plugins/`** (FileZilla / Shockbyte handoff), `server/host-handoff/plugins/`, `server/plugins/`, and `Web Files/rootmc-web/public/plugins/` (with refreshed `manifest.json`). Old plugin versions are pruned automatically in each target folder.

**Halted plugins** (`root-questionnaire`, `root-ask`, `root-blueprints`, `root-contracts`, `root-explore`) live under `halted-development/` â€” they are **not** built or copied by `publishPlugins` / `buildAllPlugins`, and any stray jars or `RootMC/*.yml` for them are removed on every build-all/publish.

## Zip this folder

From `Minecraft/server/`:

```powershell
Compress-Archive -Path host-handoff\* -DestinationPath host-handoff-rootmc-plugins.zip -Force
```

Upload `host-handoff-rootmc-plugins.zip` via Shockbyte file manager or SFTP.

## On the live host

| Local (in zip) | Live path |
|----------------|-----------|
| `plugins/rootmc-1.3.12.jar` | `plugins/rootmc-1.3.12.jar` (remove `blocknotes-*.jar` and older `rootmc-*.jar`) |
| `plugins/rootmc-shops-1.3.14.jar` | `plugins/rootmc-shops-1.3.14.jar` |
| `plugins/roothelp-1.0.6.jar` | `plugins/roothelp-1.0.6.jar` |
| `plugins/root-essentials-1.4.6.jar` | `plugins/root-essentials-1.4.6.jar` (remove EssentialsX jars during cutover) |
| `config-templates/RootMC/cloud.yml` | `plugins/RootMC/cloud.yml` â€” **set `server-id` + `server-secret` on host** |
| `config-templates/RootMC/database.yml` | `plugins/RootMC/database.yml` â€” **set `password` on host (all plugins inherit)** |
| `config-templates/RootMC/rootmc.yml` | `plugins/RootMC/rootmc.yml` â€” server behavior (no secrets) |
| `config-templates/RootMC/rootmc-shops.yml` | `plugins/RootMC/rootmc-shops.yml` |
| `config-templates/RootMC/root-essentials.yml` | `plugins/RootMC/root-essentials.yml` (optional override) |
| `config-templates/BlueMap/core.conf` | `plugins/BlueMap/core.conf` â€” **render-thread-count must be 1 on Shockbyte** |
| `config-templates/BlueMap/plugin.conf` | `plugins/BlueMap/plugin.conf` |
| `config-templates/BlueMap/webapp.conf` | `plugins/BlueMap/webapp.conf` â€” **enabled false when tiles on R2** |
| `config-templates/BlueMap/webserver.conf` | `plugins/BlueMap/webserver.conf` â€” keep for live markers (:22784) |
| `config-templates/bluemap-r2-game-server.commands` | Console after configs uploaded |
| `config-templates/RootMC/roothelp.yml` | `plugins/RootMC/roothelp.yml` (optional) |
| `config-templates/RootMC/root-rewards.yml` | `plugins/RootMC/root-rewards.yml` (optional) |
| `config-templates/RootMC/root-times.yml` | `plugins/RootMC/root-times.yml` (optional â€” Minecraft day / AFK / welcome / local web) |
| `config-templates/luckperms-setup.commands` | Run in console â€” 19 LP groups, 3 tracks (player / donor / staff) |

Disable/remove `EssentialsX` jars before restart, then restart Paper after replacing jars.

## Versions in this bundle

| Plugin | Version | Notes |
|--------|---------|-------|
| RootMC | 1.3.12 | `/rootmc link`, economy sync, `/value`, ingame capture, Discord in-game chat bridge |
| RootMC-Shops | 1.3.14 | QuickShop-style chest flow, `/buy` quotes, price cap |
| RootHelp | 1.0.6 | `/rules`, `/cmds`, `/discord`, `/map` |
| Root-Essentials | 1.4.6 | Full EssentialsX replacement â€” warps, moderation, gold economy |

Auto-update: heartbeat pulls **rootmc** from `https://rootmc.net/plugins/` (manifest). Other jars: upload from `host-handoff/plugins/` or redeploy rootmc-web Pages after `publishPlugins`.

## Do not commit secrets

`cloud.yml` on the host must contain your registered `server-id` / `server-secret`. MySQL password lives only on Shockbyte.

## Optional: Towny extras (not in this zip)

Copy separately from `Minecraft/server/config-templates/` if not already on host:

- `Towny/config.yml` + `Towny/worlds/*.txt` (overworld claimable; nether/end not)
- Towny add-ons / optional plugin configs as needed for your host
