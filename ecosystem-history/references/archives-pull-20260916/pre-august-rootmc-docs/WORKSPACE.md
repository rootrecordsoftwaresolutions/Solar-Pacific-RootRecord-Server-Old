# RootMC Workspace

Canonical workstation for RootMC development and ops.

**Path:** `D:\.1 Work Stations\RootMC\`

**Secrets:** `.env` (primary — Cloudflare, Discord, Grok, JWT). Fallback: `D:\.1 Work Stations\.credentials\.env`. Never commit.

## Layout

| Folder | Contents |
|--------|----------|
| `.env` | Primary credentials for all RootMC deploy scripts |
| `scripts/load-rootmc-env.ps1` | Shared env loader (dot-sourced by deploy scripts) |
| `Plugin Building/Minecraft/` | Live plugin source (`publishPlugins` → handoff) |
| `Web Files/rootmc-web/` | Cloudflare Pages site → [rootmc.net](https://rootmc.net) |
| `Web Files/rootmc-api/` | Worker deploy wrapper → [api.rootmc.net](https://api.rootmc.net) |
| `Web Files/rootmc-realm-api/` | Worker API source (bundled into `rootmc-api`) |
| `Web Files/shared/` | Local Worker shared-library dependency copy |
| `Web Files/rootrecord-api-account/` | Local RootRecord account/D1 dependency copy |
| `Mobile App Files/rootmc-android/` | RootMC Android app (Kotlin / Compose) |
| `Server Handoffs/1. RootMC - Claims/` | **Claims** FileZilla handoff (live-synced) |
| `Server Handoffs/2. RootMC - Towny/` | **Towny** FileZilla handoff (active prep) |
| `Server Handoffs/3. RootMC - Test Server/` | Test / local FileZilla-style staging |
| `Server Live Backups/` | Full live snapshots (include worlds) — not upload targets |
| `Change Logs/` | Server-wide + per-plugin changelogs |

Path helpers for Node scripts: `Web Files/rootmc-realm-api/scripts/lib/rootmc-paths.mjs`

Agent rules: `.cursor/rules/rootmc-workspace.mdc`

## Discord (quick post)

Bot token: `DISCORD_ROOTMC_BOT_TOKEN` in `.env`. Default channel **#updates** (`1520665313631408251`).

```powershell
powershell -File "scripts\discord-post.ps1" -Message "Your message"
```

```bat
cd "Web Files\rootmc-realm-api"
node scripts\post-discord-message.mjs --channel updates --message "Your message"
```

See `scripts/lib/rootmc-discord.mjs` for channel names and programmatic posts.

## Build & deploy

### Plugins

```bat
cd "Plugin Building\Minecraft"
.\build-with-server-jdk.bat publishPlugins
```

Jars stage to `Server Handoffs/2. RootMC - Towny/plugins/` (see `local.properties`). Copy into Claims handoff only when intentionally updating Claims.

Bump `version` in each plugin's `build.gradle.kts` before building.

### API Worker

```powershell
powershell -File "Web Files\rootmc-api\deploy.ps1"
```

### Website

```powershell
cd "Web Files\rootmc-web"
powershell -File deploy.ps1
```

### Android

```bat
cd "Mobile App Files\rootmc-android"
.\gradlew.bat bundleRelease assembleRelease
```

## Live Shockbyte sync

**FileZilla targets:** `Server Handoffs\1. RootMC - Claims\` and `Server Handoffs\2. RootMC - Towny\`.
