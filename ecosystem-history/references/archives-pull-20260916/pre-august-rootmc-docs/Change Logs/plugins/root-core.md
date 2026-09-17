# root-core

Central connection, cloud identity, ServicesManager API, and license-gate scaffold for RootMC public plugins.

**Source:** `Plugin Building/Minecraft/plugins/root-core/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.0.0] â€” 2026-07-21

### Added
- **Root-Core plugin** â€” `Root-Core.jar`; shared unit under `plugins/RootMC/` (`database.yml`, `cloud.yml`, `root-core.yml`, `license.yml`, `.core-meta.yml`).
- **`RootMcCoreConnection`** in `rootrecord-common` â€” idempotent ensure/repair (missing keys only; never clobber non-blank secrets; Towny blank-password recovery unchanged).
- **`RootCoreApi`** via Bukkit ServicesManager â€” `isReady`, `databaseSettings`, `apiBase`, `serverId`, `hasCloudCredentials`, `licenseStatus` / `isLicensed`, `ensureCoreFiles`.
- **LicenseGate stub** â€” `license.mode: operator|enforce`; operator always allows; enforce still allows (UNVERIFIED) this pass.
- **`/rootcore status|reload|license`** â€” operator UX; secrets masked.

**Deploy:** `root-core-1.0.0.jar` â€” place before other Root plugins (`loadbefore` listed). Restart. Existing Gen1 `database.yml` / `cloud.yml` passwords/secrets preserved.
