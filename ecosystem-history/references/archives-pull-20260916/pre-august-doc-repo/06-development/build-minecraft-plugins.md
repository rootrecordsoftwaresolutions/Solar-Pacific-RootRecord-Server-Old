# Minecraft plugin build & deploy

Paper plugin workspace: **`Minecraft/`** in the MonoRepo (not a separate GitHub repo today).

## Requirements

| Tool | Version |
|------|---------|
| JDK (Gradle daemon) | 17 or 21 — **not** JDK 25 |
| JDK (bytecode toolchain) | 25 — matches Paper 26.x server |
| Paper API | 26.1.2 (local jar under `server/libraries/` preferred) |

Copy `local.properties.example` → `local.properties` and set `java.version=25`. Do **not** set `org.gradle.java.home` to JDK 25.

## Build all production plugins

```powershell
cd Minecraft
.\build-with-server-jdk.bat publishPlugins
```

Artifacts land in:

- `Minecraft/out/` — local staging
- `Minecraft/server/host-handoff/plugins/` — zip for Shockbyte (see `server/host-handoff/README.md`)
- `Minecraft/server/plugins/` — local Paper test server
- `Web/apps/rootmc-web/public/plugins/` + `manifest.json` — deploy rootmc-web Pages for `https://rootmc.net/plugins/`

Do **not** upload `blocknotes-*.jar` or `plugin-template-*.jar` — retired; `publishPlugins` prunes them.

## Build single plugin

```powershell
.\build-with-server-jdk.bat :plugins:rootmc:build
```

Public wiki (commands, operator docs): `Web/main/realm/wiki/` → `/realm/wiki/` after Pages deploy.

API constant mirror: `Web/cloudflare/rootmc-realm-api/src/rootmc-server.ts` → `PLUGIN_RELEASES` (keep in sync with `manifest.json`).

## Server restart

After replacing the jar on a live Paper host:

```powershell
cd Minecraft\server
.\start_paper.bat
```

Or restart the process manager wrapping Paper.

## Layout

| Path | Role |
|------|------|
| `plugins/rootmc/` | RootMC plugin — link, stats, economy, ingame capture |
| `plugins/rootmc-shops/` | Player shops, `/buy`, Vault gold, price cap |
| `plugins/rootrecord-common/` | Shared config paths + cloud.yml helpers |
| `plugin-template/` | Scaffold for new plugins |

## Related reading

- [../02-products/minecraft-realm.md](../02-products/minecraft-realm.md)
- `Minecraft/README.md` (runbook in code repo)
