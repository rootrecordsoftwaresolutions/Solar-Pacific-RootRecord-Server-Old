# rootrecord-common

Shared library (embedded in each plugin jar) for RootMC Paper plugins.

## Config layout (`plugins/RootMC/`)

| File | Purpose |
|------|---------|
| **`cloud.yml`** | API credentials (all plugins) |
| **`database.yml`** | MySQL credentials (all plugins) |
| **`rootmc.yml`** | RootMC core — economy, McMMO, treasury, discord (no secrets) |
| `root-*.yml` | Optional per-plugin overrides; jars ship defaults |

Set **cloud** and **database** once. Plugins resolve credentials via `RootRecordCloudConfig` and `RootMcDatabaseConfig`.

Legacy `mysql:` blocks in `rootmc.yml` / `root-essentials.yml` still work until migrated.

## New plugin

1. Add `implementation(project(":plugins:rootrecord-common"))`.
2. Ship defaults as `src/main/resources/<plugin-id>.yml` (no mysql section — use `RootMcDatabaseConfig.resolve(plugin, cfg)`).
3. At enable: `RootMcDatabaseConfig.ensureDefaults(this)` if the plugin uses MySQL.
