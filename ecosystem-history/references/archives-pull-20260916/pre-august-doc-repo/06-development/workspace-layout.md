# Workspace layout (MonoRepo)

The active workspace is a single git root: `MonoRepo/`.

| Path | Role |
|------|------|
| `Mobile/` | Android/Capacitor apps, mobile docs, release build scripts |
| `Web/` | Cloudflare Workers, Pages marketing site, web app deploy tooling |
| `Web/apps/kilauea-alerts-web/` | Kilauea web app (Pages project `rootrecord-kilauea-web`) |
| `solana-rootrecord-site/` | Solana Tools Next.js app source |
| `Minecraft/` | Paper plugins (RootMC, RootStat) + local dev server |
| `Doc-Repo/` | Product/platform documentation |

## Mobile paths (typical)

- `weather-manager-mobile/` — Weather app
- `business-manager-app/` — Business Manager
- `token-manager-app/` — Token Manager
- `account-hub-app/` — Account Hub
- `rootmc-android/` — Block Notes (native Kotlin / Compose)
- `root-goals-mobile/` — Root Goals Android
- `kilauea-alerts-android/` — Kīlauea Alerts (native Kotlin)
- `builds/` — Staged release APK/AAB outputs per app token

## Web paths (typical)

- `cloudflare/rootrecord-primary/` — **Canonical** primary Worker
- `cloudflare/shared/` — Shared TS modules
- `main/` — rootrecord.info Pages site
- `cloudflare/rootmc-realm-api/` — Block Notes + Realm API shard
- `cloudflare/rootrecord-api-goals/` — Root Goals API shard
- `cloudflare/rootrecord-api-kilauea/` — Kīlauea API shard

## Minecraft (`Minecraft/`)

- `plugins/rootmc/` — production Paper plugin (heartbeat + linking + stats)
- `server/` — local Paper 26.1.2 + `start_paper.bat`

## Solana site source

The **Next.js Solana Tools app** source is in `solana-rootrecord-site/`.

## Mental map

```
Users → rootrecord.info / Play Store / solana.rootrecord.info
          ↓                      ↓
    Pages + account.js     Mobile apps (Capacitor)
          ↓                      ↓
        licence / API ←──── api.rootrecord.info (primary Worker)
```

## Related reading

- [repos-and-branches.md](repos-and-branches.md)
