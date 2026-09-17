# Root-Core

Central Paper plugin for the RootMC public suite: shared on-disk unit, MySQL + cloud identity, ServicesManager API, license bind/presence, and a stubbed enforce gate.

**Artifact:** `root-core-<version>.jar` (Gradle module `plugins/root-core`)  
**Bukkit name:** `Root-Core`

## Install

Full network checklist: **https://rootmc.net/wiki/plugins/network-setup/**

1. Drop `root-core-*.jar` (and `RootMC`) into `plugins/` (remove older `root-core-*.jar` first).
2. Put `server-name` + `product-key` in `plugins/RootMC/root-core.yml` (key from https://rootmc.net/developer/keys/).
3. Start the server. Root-Core binds + presence to **api.rootmc.net** (override via `license.api-base`) so the server appears on My Servers.
4. Run `/rootcore connect` — expect **NETWORK CONNECTED**.
5. First boot creates / repairs under **`plugins/RootMC/`** (not `plugins/Root-Core/`):
   - `database.yml` — shared MySQL
   - `cloud.yml` — **secrets** (`server-secret`, Discord bot token) + shared `api-base` / `server-id`
   - `root-core.yml` — public Cloudflare/site URLs, license mode, product key, updater
   - `license.yml` — legacy product-key + server-name fallback
   - `.core-meta.yml` — `core-connection-version` stamp

**Public URLs** (third-party hosts override these; leave RootMC defaults unless you mirror the Worker/Pages):

| Key | Default | Role |
|---|---|---|
| `cloud.api-base` | `https://api.rootmc.net` | Worker data plane |
| `license.api-base` | `https://api.rootmc.net` | Product-key bind + presence |
| `site.base` / `site.keys-path` | `https://rootmc.net` / `/developer/keys/` | Keys portal |
| `site.plugins-manifest` / `updater.manifest-url` | Pages manifest | Suite updater |

**Discord / Slack (Paper):** built into **Root-Core** as `comms`. Set `discord.bot-token`, `guild-id`, `channels.*`, and `slack.server-logs-webhook` in `cloud.yml`; chat flags in `root-core.yml` `comms:`. Use `/rootcore comms`. Remove any legacy `root-discord-*.jar`. Worker/API Discord destinations stay in Cloudflare wrangler `DISCORD_ROOTMC_*` vars — Paper does not override them.

**Gen2:** set `cloud.api-base: https://api2.rootmc.net` for the data plane. License bind/presence uses `license.api-base` (default production).

Self-repair **merges missing keys only** and **never overwrites non-blank secrets** (password, server-secret, discord bot-token). Bind writes secrets into **`cloud.yml` only**.

## Operator commands

| Command | Action |
|---|---|
| `/rootcore status` | Folder, DB (password masked), cloud identity, product-key present?, bound server-id, last presence OK/fail, updater |
| `/rootcore connect` | Pass/fail network checklist — NETWORK CONNECTED or INCOMPLETE |
| `/rootcore reload` | Reload configs, re-ensure files, rebind + reschedule presence/updater |
| `/rootcore license` | License gate + bind summary (no secrets) |
| `/rootcore update` | Run suite updater now (default on; set `updater.enabled: false` to disable) |

## Suite updater

On by default in `root-core.yml`:

```yaml
updater:
  enabled: true
  manifest-url: ""   # blank = site.plugins-manifest
  interval-hours: 6
  plugins: []          # empty = all manifest entries
  only-installed: true
  require-product-key: false
  restart-after-update: true
  restart-delay-seconds: 30
```

Set `enabled: false` to opt out. Downloads newer jars into `plugins/`, prunes older same-plugin jars, then (by default) **countdown + Paper restart** so they load. Set `restart-after-update: false` to download only. Requires `spigot.yml` `restart-script` (Root-Restart installs `./plugins/RootMC/restart-helper.sh`). Does not touch `cloud.yml` / secrets.

## Feature plugins

```yaml
softdepend: [Root-Core]
```

Prefer the ServicesManager API:

```java
RegisteredServiceProvider<RootCoreApi> rsp =
    Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
if (rsp != null) {
    RootCoreApi core = rsp.getProvider();
    var db = core.databaseSettings();
    boolean ok = core.isLicensed("Root-Times");
}
```

Compile against Core later with `compileOnly(project(":plugins:root-core"))` if needed.

### Fallback when Core is absent

Shaded `rootrecord-common` still ships the helpers:

```java
RootMcCoreConnection.ensureAndRepair(this);
RootMcDatabaseConfig.resolve(this, getConfig());
```

Log a warning that Root-Core is recommended so operators get one repair spine and `/rootcore`.

## Gen1 / Gen2

- Set MySQL password in `plugins/RootMC/database.yml`.
- Set `cloud.server-id` / `cloud.server-secret` in `cloud.yml` for heartbeats against `https://api.rootmc.net`.
- Keep `license.mode: operator` on RootMC production hosts.
- Do not overwrite live secrets from handoff stubs.

## License modes (`root-core.yml`)

| Mode | Behavior (this pass) |
|---|---|
| `operator` (default) | `isLicensed(*) == true`; log once that commercial enforce is off |
| `enforce` | Still allows plugins; status `UNVERIFIED` / grace — remote verify stubbed off |

No Stripe / Hangar / product-key minting in this module yet.

## Build

From `Plugin Building/Minecraft` (operator runs):

```bat
.\build-with-server-jdk.bat :plugins:root-core:jar
```

Or full suite: `.\build-with-server-jdk.bat publishPlugins`
