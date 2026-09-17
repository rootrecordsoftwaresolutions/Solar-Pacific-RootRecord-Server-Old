# RootMC (Block Notes Android)

**RootMC** is the in-app display name for **Block Notes** (`com.rootrecord.rootmc`) — Root Record’s native **Kotlin + Jetpack Compose** Minecraft companion: offline-first notes, coordinates, build plans, world maps, RootMC server tab (stats, shops, stock market, vault), and optional cloud features when signed into a Root Record account.

**Display name (app):** RootMC · **Play listing name:** Block Notes  
**Stable app id:** `rootrecord_rootmc_android`  
**Site:** [rootmc.net](https://rootmc.net/)  
**RootMC Discord:** https://discord.gg/rFFQYrNaqS (guild `1516108585740800042`)

## What problem it solves

- **Second brain for worlds** — Markdown notes, tags, trash, templates, and per-world organization without requiring an account.
- **Coordinates & maps** — Save labeled coords, home-screen widget, grid map from saved X/Z (works without world seed), optional Dynmap/BlueMap and Chunkbase links.
- **Build planning** — Materials lists, progress tracking, bundled reference data (blocks, items, mobs, enchantments, trades, legacy IDs).
- **Optional cloud** — Account sync, World AI reports (Grok), Realm social features, and feedback when signed in.

Core notes and worlds live in **Room** on-device. Cloud routes are additive.

## How it talks to the cloud

Production API: **`rootmc-api`** on **`https://api.rootmc.net`**

| Surface | URL |
|---------|-----|
| Worker (production) | `https://api.rootmc.net/` |
| Web proxy | `https://rootmc.net/api/*` (Pages Functions) |
| Source (monorepo) | `Web/cloudflare/rootmc-realm-api/` (bundled into `rootmc-api`) |

Representative routes:

| Route family | Purpose |
|--------------|---------|
| `POST /api/feedback` | In-app feedback → Discord |
| `GET/PUT /api/sync/snapshot` | Signed-in backup of worlds, notes, waypoints, areas |
| `/api/rootmc/realm/*` | Player profiles, friends, groups, group chat |
| `/api/rootmc/world-ai` | Grok world analysis reports (tier limits) |
| `/api/rootmc/server/*` | Featured SMP metadata + plugin heartbeat (server-authenticated) |
| `GET /v1/mobile/config` | Featured SMP + reference version + RootMC Discord invite |
| `/v1/auth/*`, `/v1/me` | Optional Root Record sign-in / membership |

Mobile config returns `support_discord_guild_id` (`1516108585740800042`), `support_discord_invite_url` (`https://discord.gg/rFFQYrNaqS`), and optional `support_discord_channel_id`.

Auth: Bearer token after Root Record sign-in; guest flows use `X-Guest-Id` where applicable.

## Monetization

- **Free** — Full offline core; ads (AdMob banner + interstitial on navigation).
- **Pro / Lifetime** — Ads hidden; higher World AI quotas; Realm group limits (see app copy).

## Code locations (MonoRepo)

| Layer | Path |
|-------|------|
| Android app | `Mobile/rootmc-android/` |
| API Worker (deploy) | `Web/cloudflare/rootmc-api/` (source in `rootmc-realm-api/`) |
| Marketing page | `Web/main/rootmc.html` |
| Staged releases | `Mobile/builds/rootmc/` |

## Build & release

```powershell
cd Mobile/rootmc-android
.\gradlew.bat assembleDebug
# Signed Play release:
.\bump-and-build-release.bat
```

Requires **JDK 17**, Android SDK (`local.properties` → `sdk.dir`), and gitignored upload keystore for release.

Current baseline in repo: **versionName 1.0.20** (versionCode 20) — verify `app/build.gradle.kts` before shipping.

## Realm & RootMC integration

RootMC pairs with the **RootMC** site ([rootmc.net](https://rootmc.net/)) and **RootMC + rootmc-shops** on the dedicated SMP. Players link in-game with `/rootmc link`, verify at [rootmc.net/verify/](https://rootmc.net/verify/), and view public stats at `/player/{uuid}`.

**Wiki (all commands):** [rootmc.net/wiki/player/#commands](https://rootmc.net/wiki/player/#commands)

See [minecraft-realm.md](minecraft-realm.md) for server plugins, economy, vault/stock market, and heartbeat details.

## Related reading

- [minecraft-realm.md](minecraft-realm.md) — RootMC server, plugins, linking, McMMO, shops, wiki
- [../06-development/build-and-release-mobile.md](../06-development/build-and-release-mobile.md)
- [../03-platform/api-overview.md](../03-platform/api-overview.md)
- [RootMC wiki](https://rootmc.net/wiki/)
