# Root-Times

Public RootMC plugin (**1.0.1**): Minecraft-day clock, world daylight sync, playtime seconds / AFK / activity harvest, join welcome lines, and a local status webpage.

Updates via Root-Core suite updater (`manifest.json` entry `root-times`).

## Install

1. Install [Root-Core](https://rootmc.net/plugins/root-core/) first (recommended).
2. Drop `root-times-<version>.jar` into `plugins/` (delete older same-plugin jars).
3. Configure `plugins/RootMC/root-times.yml` and shared `database.yml`.
4. Restart Paper. Run `/times status`.

Guide: https://rootmc.net/plugins/root-times/

## Soft-depend

```yaml
softdepend: [Root-Core, PlaceholderAPI]
```

Uses `RootCoreApi` when Core is present; otherwise `RootMcCoreConnection.ensureAndRepair` + console warning.

## Ownership

When Root-Times is enabled:

- RootMC skips `McDayWorldSync` and playtime session writers
- Root-Activity skips hourly activity writers (`/timezone` remains)
- Root-Essentials `/afk` soft-bridges to Times AFK state
