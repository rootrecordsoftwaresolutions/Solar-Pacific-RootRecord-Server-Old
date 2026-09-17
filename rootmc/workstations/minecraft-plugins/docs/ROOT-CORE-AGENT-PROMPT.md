# Agent prompt: Root-Core â€” state-of-the-art central plugin for RootMC

**Copy everything below the line into a new agent chat.** That agent should implement `Root-Core` only (not Root-Times). Return when Core is solid; a sibling plan will add Root-Times on top.

---

## Mission

Build **`Root-Core`** (`Root-Core.jar`) â€” the single central Paper/Bukkit plugin for the RootMC public plugin ecosystem.

It is the **runtime spine** of the suite:

1. **One shared on-disk unit** under `plugins/RootMC/` (create + self-repair if missing/corrupt).
2. **One MySQL credential source** for every Root plugin (`database.yml`).
3. **One cloud / server identity source** (`cloud.yml`) aligned with existing heartbeat/register APIs.
4. **A public ServicesManager API** (`RootCoreApi`) other Root plugins soft-depend on.
5. **A license / product-key gate scaffold** ready for monthly commercial entitlements later â€” **stubbed open** for Gen1/Gen2 operator mode this pass (do not hard-block plugins yet).
6. **Self-healing**: if Core or key YAML files are missing, recreate safe defaults without wiping live secrets; log clear operator guidance.

Quality bar: production-grade Paper 1.21+ plugin â€” clean package layout, defensive I/O, async-safe DB helpers, zero secret leakage in logs, minimal surface area, excellent operator UX (`/rootcore`).

---

## Workspace & generation rules (non-negotiable)

| Item | Value |
|---|---|
| Canonical workspace | `D:\.1 Work Stations\RootMC\` |
| Plugin source tree | `D:\.1 Work Stations\RootMC\Plugin Building\Minecraft\` |
| New module path | `Plugin Building/Minecraft/plugins/root-core/` |
| Shared library | `Plugin Building/Minecraft/plugins/rootrecord-common/` (already shaded into Root jars) |
| Live Towny handoff | `D:\.1 Work Stations\RootMC\2. RootMC - Towny\` â€” **do not edit credentials** unless operator explicitly asks |
| Live Claims handoff | `D:\.1 Work Stations\RootMC\1. RootMC - Claims\` â€” live-synced; leave unless asked |
| Gen2 prototype source | `gen2-27/` â€” **out of scope** unless asked |
| Secrets | Never commit `.env`, passwords, bot tokens, keystores |
| Player currency copy | **Gold (G)**, never dollars |
| Builds / deploys / commits | **Do not run** unless the operator explicitly asks |
| Discord posts | Only if operator asks |

RootMC API is `https://api.rootmc.info` (`rootmc-api` / `rootmc-realm-api`). Do **not** wire RootRecord product shards (`rootrecord-api-*`).

Related sibling plan (do **not** implement in this chat unless asked): Root-Times for MC-day clock, AFK, activity harvest, local status web. Core must be ready for Times to `softdepend: [Root-Core]`.

---

## Product intent

### Why Root-Core exists

Public Root plugins must work as **one self-repairing unit**, not N independent â€œmini-cores.â€ Operators install **`Root-Core.jar` once**. Feature plugins (Essentials, Times, Bonds, â€¦) soft-depend it.

Future commercial model:

- Developer / customer account on rootmc.net
- Product key / serial â†’ entitlement (plugin set, plan, period, max servers)
- Bind key to `server_id`
- Root-Core verifies with API, caches a **signed offline lease**, soft-disables commercial features when unpaid
- Own RootMC network stays operator-unlimited

**This pass:** scaffold Core + connection self-repair + API + **LicenseGate stub always allowing** (or â€œoperator modeâ€). Do **not** implement Stripe/billing UI yet; leave clean extension points.

### What â€œstate of the artâ€ means here

- Single responsibility: connection, identity, license scaffold, shared services â€” **not** economy, Towny, or gameplay features
- Idempotent bootstrap; crash-safe YAML merge
- Clear versioning stamps on core files
- Bukkit ServicesManager as the cross-plugin contract (not reflection soup)
- Fail loud in console, fail soft for gameplay when optional pieces missing
- Documented `loadbefore` so Core enables before other Root plugins
- Compatible with existing live hosts that already have `plugins/RootMC/database.yml` + `cloud.yml`

---

## Existing code you must reuse (do not reinvent)

Study and extend these before writing parallel copies:

### Folders & files

- [`RootRecordFolders`](Plugin Building/Minecraft/plugins/rootrecord-common/src/main/java/com/rootrecord/minecraft/common/RootRecordFolders.java) â€” all configs live under `plugins/RootMC/`, not `plugins/Root-Core/`
- Bundled defaults:
  - [`database.yml`](Plugin Building/Minecraft/plugins/rootrecord-common/src/main/resources/database.yml)
  - [`cloud.yml`](Plugin Building/Minecraft/plugins/rootrecord-common/src/main/resources/cloud.yml)

### MySQL resolution

- [`RootMcDatabaseConfig`](Plugin Building/Minecraft/plugins/rootrecord-common/src/main/java/com/rootrecord/minecraft/common/config/RootMcDatabaseConfig.java)
  - `ensureDefaults`, `resolve`; password must be set in `plugins/RootMC/database.yml` (no secondary password source)
  - Precedence: plugin yaml â†’ `database.yml` â†’ legacy rootmc/essentials
- [`MysqlConnections`](Plugin Building/Minecraft/plugins/rootrecord-common/src/main/java/com/rootrecord/minecraft/common/mysql/MysqlConnections.java) â€” prefer extending / wrapping over new pool code

### Cloud / server auth (already live)

- `plugins/RootMC/cloud.yml` fields: `cloud.api-base`, `cloud.server-id`, `cloud.server-secret`
- API: `POST /api/rootmc/server/register`, heartbeat via `X-RootStat-Server-Id` (see RootMC `CloudHeartbeatClient`)
- Player/web accounts already use `license_accounts` on the Worker â€” **reuse concepts** later for developer product keys; do not fork a second account system

### Build patterns

- Modules under `plugins/*` are auto-scanned by Gradle settings
- Mirror a simple public plugin `build.gradle.kts` (e.g. `root-essentials`): Java plugin, shade mysql connector if Core opens JDBC, `api-version` matching siblings (`26.1` style in `plugin.yml`)
- Plugin display name: **`Root-Core`** (Bukkit `getPlugin("Root-Core")`)

---

## Deliverables (this agent)

### 1. New module `plugins/root-core/`

Suggested layout:

```text
plugins/root-core/
  build.gradle.kts
  src/main/resources/
    plugin.yml
    root-core.yml          # optional plugin-local defaults â†’ copied to plugins/RootMC/root-core.yml
    database.yml           # can re-export common default if needed for ClassLoader resource
    cloud.yml
  src/main/java/com/rootrecord/minecraft/rootcore/
    RootCorePlugin.java
    api/RootCoreApi.java
    api/RootCoreApiImpl.java
    connection/RootMcCoreConnection.java   # or live in rootrecord-common (preferred)
    license/LicenseGate.java               # stub
    license/LicenseStatus.java
    command/RootCoreCommand.java
    cloud/CloudConfigEnsure.java
```

Prefer placing **`RootMcCoreConnection`** in **`rootrecord-common`** so feature jars can call the same ensure/repair if Core is absent (fallback path). Root-Core plugin is the **primary caller** and ServicesManager owner.

### 2. `plugin.yml` requirements

```yaml
name: Root-Core
main: com.rootrecord.minecraft.rootcore.RootCorePlugin
version: '1.0.0'   # or match project versioning style
api-version: '26.1'
author: RootMC
description: Central connection, cloud identity, and license spine for RootMC plugins.
softdepend: []
loadbefore:
  - RootMC
  - Root-Essentials
  - Root-Activity
  - Root-Bonds
  - Root-Loans
  - Root-Admin
  - Root-Times
  # include other Root-* public plugins as known
commands:
  rootcore:
    description: Root-Core status and reload
    usage: /rootcore <status|reload|license>
    permission: rootcore.admin
permissions:
  rootcore.admin:
    default: op
```

### 3. On-disk self-repair contract

Canonical directory: **`plugins/RootMC/`** (via `RootRecordFolders`).

| File | Behavior |
|---|---|
| `database.yml` | Create from jar default if missing. If present: merge **missing keys only**; **never overwrite non-blank password**. Stamp `# core-connection-version: N` in header or a sibling `.core-meta.yml` |
| `cloud.yml` | Create stub if missing. Never blank out existing `server-secret`. Merge missing keys only |
| `root-core.yml` | Plugin settings (log level, license mode, repair toggles) |
| `license.yml` (optional this pass) | Create empty scaffold (`product-key: ""`, `lease: {}`) for future; do not require it to enable |

Implement `RootMcCoreConnection.ensureAndRepair(JavaPlugin)`:

- Idempotent
- Thread-safe enough for onEnable
- Logs: created / repaired keys / skipped (secret present); warn if `database.yml` password blank
- Returns a small result object: `{ databaseOk, cloudOk, warnings[] }`

### 4. `RootCoreApi` (Bukkit ServicesManager)

Register on enable; unregister on disable. Other plugins resolve via:

```java
RegisteredServiceProvider<RootCoreApi> rsp =
    Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
```

Minimum interface (adjust names to house style):

```java
public interface RootCoreApi {
  boolean isReady();
  RootMcDatabaseConfig.DatabaseSettings databaseSettings(); // or a stable DTO in api package
  String apiBase();
  String serverId();
  boolean hasCloudCredentials();
  LicenseStatus licenseStatus(String pluginId);
  boolean isLicensed(String pluginId); // stub: true when mode=operator or no key required
  void ensureCoreFiles(); // re-run repair
}
```

Keep API in a package other plugins can compile against. Prefer `compileOnly(project(":plugins:root-core"))` for siblings later, **or** a tiny `root-core-api` sourceset â€” start simple: API classes in root-core that are stable; common stays for file helpers.

### 5. LicenseGate stub (extension point)

```text
modes (root-core.yml):
  license.mode: operator | enforce
```

- **`operator`** (default): `isLicensed(*) == true`; log once â€œoperator mode â€” commercial enforce disabledâ€
- **`enforce`**: still **allow** this pass if no key / API missing, but log WARN and set `LicenseStatus` to `UNVERIFIED` / `GRACE` so later work only flips the boolean

Do **not** call Stripe. Optional: soft HTTP stub method `verifyAsync()` that no-ops or hits a future `/api/rootmc/license/verify` behind a feature flag default off.

### 6. Commands / operator UX

`/rootcore status` â€” folder path, database host/db/user (mask password), cloud api-base, server-id present?, license mode, core-connection-version, list of detected Root-* plugins  
`/rootcore reload` â€” reload root-core.yml + re-ensure files + refresh API snapshot  
`/rootcore license` â€” show stub status + how to set product key later  

Messages: readable, colored sparingly, no secrets.

### 7. Integration expectations (document in module README)

Add `Plugin Building/Minecraft/plugins/root-core/README.md` covering:

- Install: drop `Root-Core.jar` into `plugins/`
- First boot creates `plugins/RootMC/database.yml` + `cloud.yml`
- Feature plugins should `softdepend: [Root-Core]` and prefer `RootCoreApi` for DB settings
- Fallback: if Core absent, call `RootMcCoreConnection.ensureAndRepair` from shaded common and warn
- Set password in `database.yml`; set `server-id` / `server-secret` in `cloud.yml`

### 8. Tests / verification (operator runs builds)

You may compile if the operator asks; otherwise describe verification:

1. Empty `plugins/RootMC/` â†’ enable Core â†’ files created with stamps  
2. Corrupt/partial `database.yml` missing `port` â†’ repair fills port, keeps password  
3. Existing live-like file with password â†’ password unchanged after reload  
4. `/rootcore status` masks secrets  
5. Second plugin can resolve `RootCoreApi` when both loaded (smoke with a tiny test or manual)  
6. Disable Core â†’ server still starts; no hard crash from missing service  

---

## Explicit non-goals (this agent)

- Do **not** build Root-Times, AFK, activity writers, or local HTTP status UI  
- Do **not** move Towny / Bonds / Upkeep / economy into Core  
- Do **not** implement full product-key minting, Stripe, or Hangar publishing  
- Do **not** change Gen1/Gen2 live `database.yml` / `cloud.yml` passwords on disk under `D:\Gen1` / `D:\Gen2`  
- Do **not** commit secrets or run `commit-all.bat` / FileZilla / Shockbyte restart  
- Do **not** post to Discord  
- Do **not** redesign RootRecord license login for mobile apps  

---

## Design constraints & quality checklist

- [ ] All persistent Root suite config under `plugins/RootMC/` only  
- [ ] Self-repair never clobbers non-blank secrets  
- [ ] Blank `database.yml` password warns clearly; password is required there  
- [ ] ServicesManager API registered before other Root plugins need it (`loadbefore`)  
- [ ] Soft-depend model for consumers; Core itself has no hard Root deps  
- [ ] Async: any network verify later must not block main thread  
- [ ] Logging: INFO for create/repair summary; WARNING for missing password / missing cloud; no password/secret values  
- [ ] Minimal diff elsewhere: only touch other plugins if required for `loadbefore` lists / README pointers  
- [ ] Match existing code style (packages `com.rootrecord.minecraft.*`, Java, Gradle Kotlin DSL)  

---

## Suggested implementation order

1. Add `RootMcCoreConnection` (+ tests of merge logic if feasible without full server) in `rootrecord-common`  
2. Scaffold `root-core` module, `plugin.yml`, `RootCorePlugin` onEnable/onDisable  
3. Ensure database + cloud files; register `RootCoreApi`  
4. LicenseGate stub + `root-core.yml`  
5. `/rootcore` command  
6. README + short changelog note under `Change Logs/` if that is the house pattern  
7. Stop. Report what was built, file paths, and how the Root-Times agent should softdepend  

---

## Future license architecture (implement hooks only)

```text
Developer account (license_accounts)
    â†’ product key / serial
        â†’ entitlement (plugins[], plan, expires_at, max_servers)
            â†’ bind(server_id)
                â†’ Root-Core writes license.yml + signed lease cache
                    â†’ feature plugin asks RootCoreApi.isLicensed("Root-Times")
```

Reuse existing cloud register/heartbeat identity. Commercial gate is for **external** customers; RootMC production stays `license.mode: operator`.

---

## Success definition

Operator can drop **only** `Root-Core.jar` on a fresh Paper server and get a healthy `plugins/RootMC/` tree with repairable `database.yml` + `cloud.yml`, a working `/rootcore status`, and a ServicesManager API ready for Root-Times and other public plugins â€” without a second â€œcore dependencyâ€ file per plugin, and without breaking existing Gen1 hosts that already have those YAML files populated.

---

## Handoff back to Root-Times agent

When done, summarize for the next agent:

- Artifact paths and plugin version  
- Exact `RootCoreApi` method list  
- How Times should call `ensureAndRepair` / softdepend  
- Any follow-ups left for product-key enforce mode  

Then stop; do not start Root-Times unless the operator explicitly expands scope.
