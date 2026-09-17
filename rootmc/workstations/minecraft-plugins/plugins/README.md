# Plugins

Paused plugins (`root-questionnaire`, `root-ask`, `root-blueprints`, `root-contracts`, `root-explore`) live under [`../halted-development/`](../halted-development/README.md) â€” not built by `publishPlugins`.

Add each active Paper plugin as its own subfolder here, for example:



```

plugins/

  earthmc-bridge/

    build.gradle.kts

    src/main/java/...

    src/main/resources/plugin.yml

```



Copy `../plugin-template/` as a starting point, then run from the repo root:



```powershell

.\gradlew.bat :plugins:earthmc-bridge:build

```



Built jars are copied to `Minecraft/out/` and `server/plugins/` (via `deployToServer`).



## Shared config folder



RootMC Paper plugins use **`plugins/RootMC/`** on the server (one folder, not per-plugin subfolders):

| File | Role |
|------|------|
| **`cloud.yml`** | Shared API credentials |
| **`database.yml`** | Shared MySQL credentials |
| **`rootmc.yml`** | Core server plugin settings (no secrets) |
| `root-*.yml` | Optional overrides per plugin (defaults ship in jars) |

New plugins should use the `rootrecord-common` module â€” see [`rootrecord-common/README.md`](rootrecord-common/README.md).



## RootMC (sole RootRecord Paper plugin)



[`rootmc/`](rootmc/) â€” account linking (`/rootstat`), McMMO + playtime sync, Vault economy, Root Shops sign scans, PlaceholderAPI, Block Notes app heartbeat, and remote jar updates.



Java packages under `com.rootrecord.minecraft.rootstat.*` are internal modules inside this single jar (linking, economy, sync) â€” not a separate plugin.



```powershell

.\build-with-server-jdk.bat :plugins:rootmc:build

```



Output: `out/rootmc-1.3.0.jar` (also copied to `Server Handoffs\2. RootMC - Towny/plugins/` on build). Ignore `server/host-handoff/plugins/` unless you use it yourself.

### Shop plugin integration (plug-and-play)

RootMC auto-detects **QuickShop / QuickShop-Hikari** and **ChestShop** via reflection (no compile-time dependency). Configure in `rootmc.yml`:

- `economy.shop-provider: auto` â€” first match in `shop-providers-priority` (default: quickshop â†’ chestshop â†’ sign fallback)
- `merge` â€” combine all detected providers
- `vault-enabled: true` â€” Vault balances for net-worth leaderboard

Install Vault + your shop plugin; register the server at rootrecord.info; no extra RootMC shop jar required.

## Root-Rewards

[`root-rewards/`](root-rewards/) â€” playtime milestone gold (15m â†’ 65536h, doubling each tier) and vote rewards (20G default). Reads playtime from RootMC MySQL (`root_rootmc_playtime`). Requires Root-Essentials economy + NuVotifier/VotifierPlus for vote payouts.

```powershell
.\build-with-server-jdk.bat :plugins:root-rewards:build
```

Commands: `/rewards`, `/vote`, `/rootrewards reload` (op).

## Root-Loans

[`root-loans/`](root-loans/) â€” personal loans with **10% interest**, income sweep (100% of incoming G while in debt), and gold-ore repayment. Integrates with Root-Essentials via `RootMcLoanService`. Enabled on RootMC after a community vote.

```powershell
.\build-with-server-jdk.bat :plugins:root-loans:build
```

Commands: `/loan take|info|repay|list`, `/rootloans reload` (op). Config: `plugins/RootMC/root-loans.yml`.

## Root-Announcer

[`root-announcer/`](root-announcer/) â€” rotating server broadcasts from `plugins/RootMC/root-announcer.yml`.

```powershell
.\build-with-server-jdk.bat :plugins:root-announcer:build
```

Commands: `/rootannouncer reload`, `/rootannouncer list`, `/rootannouncer now [index]` (aliases: `/rannounce`, `/announce`). Placeholders in lines: `{online}`, `{max}`.

## Root-Admin

[`root-admin/`](root-admin/) â€” staff moderation/admin commands (moved out of Root-Essentials) plus `/report <player> [reason]` with last 30 minutes of **reported player** chat, commands, and CoreProtect history.

```powershell
.\build-with-server-jdk.bat :plugins:root-admin:build
```

Requires **Root-Essentials** at runtime. Config: `plugins/RootMC/root-admin.yml`. Reports saved to `plugins/RootMC/reports/`. Staff notify permission: `rootadmin.reports.notify`.


