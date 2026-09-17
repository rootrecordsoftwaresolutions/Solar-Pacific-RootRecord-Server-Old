# Root Record â€” Minecraft plugins (Gen 1 26)

**Live production** plugin workspace. `publishPlugins` deploys to `Server Handoffs\2. RootMC - Towny/` and rootmc.net â€” **never** `gen2-27/` (Gen 2).

See `../../GEN-1-GEN-2.md`. Gen 2 prototype: `../../gen2-27/Plugin Building/Minecraft/`.

Java **Paper** plugin workspace. Local dev server lives in **`server/`** (Paper **26.2**, Java **25**).

## Layout

| Path | Purpose |
|------|---------|
| `plugins/` | Plugin source projects (one folder per plugin) |
| `plugin-template/` | Copy into `plugins/<name>/` to start |
| `server/` | Your Paper server (`start_paper.bat`) |
| `server/plugins/` | Deploy target for built jars |
| `out/` | Copy of built jars (convenience) |

## Requirements

- **JDK 25** â€” same as `server/start_paper.bat` (Paper 26.x)
- Copy `local.properties.example` â†’ `local.properties` if Gradle should use a specific JDK path

## Build & deploy

From `Minecraft/`:

```powershell
# Uses Java 25 when installed at the Temurin path from start_paper.bat
.\build-with-server-jdk.bat :plugin-template:build

# Build every plugin + copy jars to server/plugins/
.\build-with-server-jdk.bat buildAllPlugins
```

Then start or restart the server:

```powershell
cd server
.\start_paper.bat
```

Gradle prefers the **exact `paper-api` jar** under `server/libraries/` when present, so plugins compile against the same API as your running server.

## New plugin

1. Copy `plugin-template/` â†’ `plugins/my-plugin/`
2. Set `name`, `main`, and `api-version` in `plugin.yml`
3. Build: `.\build-with-server-jdk.bat :plugins:my-plugin:build`

`settings.gradle.kts` auto-includes any `plugins/*/build.gradle.kts`.

## Versions

Configured in `gradle.properties`:

- `paperApiVersion=26.2.build.60-beta`
- `paperApiBuild=26.2.build.60-beta` (local jar path under `server/libraries/` when present)
- `javaVersion=25`

See also `server/README.md` for server-specific notes.
