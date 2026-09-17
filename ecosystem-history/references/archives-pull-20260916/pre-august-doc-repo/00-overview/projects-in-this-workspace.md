# Projects in the RootRecord workspace (snapshot)

This page summarizes what lives under **`MonoRepo/`** when building RootRecord.

## Mobile monorepo (`Mobile/`)

| Path | Project |
|------|---------|
| `weather-manager-mobile/` | Weather Manager — alerts, forecasts, hazard bundles |
| `business-manager-app/` | Business Manager — operations, finance, scheduling |
| `token-manager-app/` | Token Manager — Solana mobile wallet UX |
| `account-hub-app/` | Account Hub — account spine + subscriptions shortcuts |
| `rootmc-android/` | RootMC (Block Notes) — native Kotlin Minecraft companion |
| `root-goals-mobile/` | Root Goals — Android client |
| `kilauea-alerts-android/` | Kīlauea Alerts — native Kotlin volcano alerts |

Shared tooling: **`pnpm`** (Capacitor apps), **Gradle** (native Kotlin apps), staged releases under **`builds/`** per app token.

## Web workspace (`Web/`)

| Path | Project |
|------|---------|
| `cloudflare/rootrecord-primary/` | Primary API Worker (`api.rootrecord.info`) |
| `cloudflare/rootrecord-license/` | Licence / companion auth Worker |
| `cloudflare/rootrecord-app-build/` | App build request Worker |
| `cloudflare/shared/` | Shared Worker TS utilities |
| `main/` | **rootrecord.info** — Cloudflare Pages static site |

Solana Tools Next.js source is at **`solana-rootrecord-site/`** at the monorepo root.

## Minecraft plugins (`Minecraft/`)

| Path | Project |
|------|---------|
| `plugins/rootmc/` | RootMC Paper plugin (linking, economy, McMMO, ingame capture, heartbeat) |
| `plugins/rootmc-shops/` | Chest shops, `/buy`, price caps |
| `plugins/rootrecord-common/` | Shared `plugins/RootRecord/` config helpers |
| `server/` | Local Paper 26.1.2 dev server |

Public wiki (commands + operator docs): **`Web/apps/rootmc-web/public/wiki/`** → [rootmc.net/wiki/](https://rootmc.net/wiki/)

## This documentation repo (`Doc-Repo/`)

Markdown-only knowledge base you are reading—safe to open-source without application secrets.

## Related reading

- [../06-development/workspace-layout.md](../06-development/workspace-layout.md)
