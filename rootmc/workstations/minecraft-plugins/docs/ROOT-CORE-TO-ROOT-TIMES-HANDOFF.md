# Handoff: Root-Times done (after Root-Core)

**Paste into a new agent chat** when continuing suite work. Do **not** rebuild Root-Core or Root-Times unless asked. Do **not** change Gen1/Gen2 credentials unless the operator asks.

---

## What shipped

### Root-Core â€” live **1.2.5** (unchanged spine)
- Module: `Plugin Building/Minecraft/plugins/root-core/`
- Shared unit: `plugins/RootMC/` (`root-core.yml`, `database.yml`, `cloud.yml`)
- `RootCoreApi` + suite updater â†’ `https://rootmc.net/plugins/manifest.json`
- Guide: https://rootmc.net/plugins/root-core/

### Root-Times â€” **1.0.1** (this pass)
- Module: `Plugin Building/Minecraft/plugins/root-times/`
- Soft-depends **Root-Core**; fallback `RootMcCoreConnection.ensureAndRepair` + warn
- Owns: McDayClock configure + world daylight sync, **total playtime seconds** + activity harvest, idle AFK, join welcome (2 lines), local web UI
- CDN / auto-update: listed in `heartbeatManifestPluginNames` â†’ `https://rootmc.net/plugins/manifest.json` + `root-times-1.0.1.jar`
- Guide: https://rootmc.net/plugins/root-times/ (source under `Web Files/rootmc-web/public/plugins/root-times/`)
- Config: `plugins/RootMC/root-times.yml`
- Web docroot: `plugins/RootMC/times/web/` (defaults copied once; never overwrite edits)
- Default web bind: `127.0.0.1:8765` â€” `/api/status`, `/api/players`, `/api/timezones`
- Commands: `/times` `| reload | web`, `/afk` (when Essentials absent; Essentials soft-bridges when present)
- API: `RootTimesApi` on ServicesManager

### Integrations (minimal)
| Plugin | Behavior when Root-Times enabled |
|---|---|
| RootMC | Skips `McDayWorldSync`; skips playtime session writers; skips `McDayClock.configure` overwrite |
| Root-Activity | Skips hourly activity writers; `/timezone` reads remain |
| Root-Essentials | `/afk` + `isAfk` soft-bridge via reflection |

### Public site (source ready â€” operator deploys)
- Catalog entry `available`: `Web Files/rootmc-web/public/plugins/plugins.js`
- Guide: `Web Files/rootmc-web/public/plugins/root-times/index.html` â†’ https://rootmc.net/plugins/root-times/
- CDN list: `root-times` in `heartbeatManifestPluginNames` (`Plugin Building/Minecraft/build.gradle.kts`)
- Host template: `server/host-handoff/config-templates/RootMC/root-times.yml`

### Join welcome (locked copy)
1. `Total Playtime: {playtime} - Your Current Time: H:MM AM/PM {timezone}`
2. `Minecraft day #{dayId} - Ingame time: {todTicks} ({phase})`

---

## Workspace paths

| Item | Path |
|---|---|
| Workspace | `D:\.1 Work Stations\RootMC\` |
| Plugins | `Plugin Building/Minecraft/` |
| Times module | `Plugin Building/Minecraft/plugins/root-times/` |
| Core module | `Plugin Building/Minecraft/plugins/root-core/` |
| Common | `Plugin Building/Minecraft/plugins/rootrecord-common/` |
| Site | `Web Files/rootmc-web/` |
| Gen1 / Gen2 | `D:\Gen1\` / `D:\Gen2\` â€” **do not change credentials** unless asked |

Rules: Gold not dollars; no Discord/build/deploy/commit unless operator asks.

---

## Operator next steps (you run)

```bat
cd "Plugin Building\Minecraft"
.\build-with-server-jdk.bat publishPlugins
```

1. Stage **only** intended jars into `D:\Gen1\plugins\` (and Gen2 if asked); prune older `root-times-*.jar`
2. FileZilla upload + Paper restart (twice if Core updater downloads)
3. Site deploy when CDN/catalog should go live (`Web Files/rootmc-web` deploy)
4. Verify in-game: `/times status`, join welcome lines, optional `http://127.0.0.1:8765/` on the host

---

## Quick verify

- Jar: single `root-times-1.0.0.jar` (after publish)
- Config: `plugins/RootMC/root-times.yml`
- Console: Times enables; RootMC logs skip world sync when Times present
- `/times` shows day id + ticks; MySQL ready when `database.yml` set
- Docs: `plugins/root-times/README.md` + https://rootmc.net/plugins/root-times/ (after site deploy)

---

## Out of scope unless asked

- Stripe / hard license enforce
- Gen2 mirror of Times
- Cloud heartbeat MC-day push for site Gen1/Gen2 cards
- Changing Gen1/Gen2 live secrets
- Redeploying Core â€œfor funâ€
