# RootMC local-edge ops runbook

## Phase 0 (confirmed 2026-07-24)

See [PHASE0-FINDINGS.md](PHASE0-FINDINGS.md): `api.rootmc.net` returned **429**; site JSON empty because API is throttled.

## Local MySQL (tier-2)

- **Datadir:** `D:\.database\data` (parent `D:\.database` â€” leaf cannot be a dotted name; MySQL InnoDB bug)
- **Config:** `D:\.mysql-config\my.ini`
- **Port:** `3307` (localhost only)
- **Creds:** `ROOTMC_LOCAL_MYSQL_*` in Workspace `.env`
- **Schemas:** `rootmc_claims`, `rootmc_towny` (synced snapshots), `rootmc_dev` (ROOTMC DEV test server), `rootmc_network` (registry)
- **DEV ensure:** `powershell -File scripts\local-edge\Ensure-RootMcDevMysql.ps1`
- **Install/repair:** `powershell -File scripts\local-edge\Install-LocalMySQL.ps1`
- **Autostart:** scheduled task `RootMC Local MySQL`

## ROOTMC DEV / Local Testing (Desktop SSD)

- **Canonical Paper folder:** `C:\Users\store\Desktop\RootMC test server`
- **Workspace junction:** `D:\.1 Work Stations\RootMC\test server` â†’ Desktop folder
- **Move helper:** `powershell -File scripts\Move-TestServerToDesktop.ps1`
- **Profile:** combined Claims + Towny jars via `sync-plugins.ps1` (Towny XOR Claims for territories)
- **UI:** Root-Core-Node â†’ **Local Testing** tab
- **Node install (co-located):** `C:\Users\store\Desktop\RootMC test server\Root-Core-Node\`
  - Exe + `config.json` + `state\` + `logs\node.log`
  - Desktop shortcut `Root-Core-Node.lnk` â†’ this folder
  - Reinstall: `powershell -File scripts\root-core-node\install-root-core-node.ps1`
- **Port:** `127.0.0.1:25565` Â· Playit public: see `playit-address.txt` / `playit\README.md`
- **Pre-run hold doc:** `Change Logs\Pre-Run Report Local Testing 07-25-2026.txt`
- **Launch ready doc:** `Change Logs\Launch Ready Report Local Testing 07-25-2026.txt`

### Local Testing tab (Root-Core-Node)

Operator UI for ROOTMC DEV Paper on Desktop SSD:

| Action | What it does |
|--------|----------------|
| Start server | `start.bat` (âˆ’Xms1G âˆ’Xmx3G, Java 25, Paper 26.2-62) |
| Boot once | `boot-once.ps1` until Done, then stop |
| Stop server | Kill process listening on `:25565` / Node-owned tree |
| Sync plugins | Copy `root*.jar` + Vault + PAPI from Towny handoff |
| Ensure MySQL schema | `Ensure-RootMcDevMysql.ps1` â†’ `rootmc_dev` + identity |
| Open folder / Times web | Explorer + `http://127.0.0.1:8765/` |

Status panel shows path, jar, bind, playit address, MOTD, online-mode, schema port.

## Auto-start (logon)

```powershell
powershell -File "D:\.1 Work Stations\RootMC\scripts\dev-workstation\install-dev-workstation.ps1"
```

Scheduled task **RootMC Dev Workstation** (At logon, ~45s delay) opens a **visible** PowerShell window titled **RootMC Local Edge**:

1. Starts local-edge backends (api / api2 / map / site / gateway / preference loop)
2. Runs the edge terminal UI in that window
3. Runs workstation presence headless in the background

**Windows:** 1 visible (Local Edge console + terminal). Stack processes + presence are Hidden.

**Discord:** boot posts â€œpowered on / syncingâ€¦â€. Preference loop posts **Dev connection: local Â· online** only after D1 sync markers are fresh + edge healthy; if sync goes stale or health fails â†’ **Cloudflare fallback** post + preference.

JSON status (no HttpListener): `http://127.0.0.1:8791/__edge/status`

Tunnel starts only after you replace `REPLACE_WITH_TUNNEL_ID` in `cloudflared/config.yml` (cloudflared is installed).

## Quick start (local only â€” no DNS change)

```powershell
cd "D:\.1 Work Stations\RootMC\scripts\local-edge"
# Optional: seed local D1 from remote (needs working wrangler auth; may fail while CF is 429)
# powershell -File .\Bootstrap-LocalD1.ps1

powershell -File .\Start-LocalEdge.ps1 -SkipTunnel
```

Verify:

- http://127.0.0.1:8791/__edge/health
- http://127.0.0.1:8791/__edge/preference
- http://127.0.0.1:8791/__edge/cache-stats
- http://127.0.0.1:8790/ (site)
- http://127.0.0.1:8787/health (wrangler API)
- http://127.0.0.1:8792/status (terminal JSON)

Stop:

```powershell
powershell -File .\Stop-LocalEdge.ps1
```

## Preference control

| Mode | How |
|------|-----|
| auto (default) | health + HST schedule windows in `config.json` |
| force local | `Set-Content state\force_mode.txt local` or `GET http://127.0.0.1:8792/force/local` |
| force cloudflare | `force_mode.txt` = `cloudflare` |
| back to auto | `force_mode.txt` = `auto` |

Mirrored public read (after Worker deploy): `GET https://api.rootmc.info/api/rootmc/connection-preference`  
Local writer: `PUT` same path with `Authorization: Bearer $ROOTMC_DEV_WORKSTATION_KEY`.

## Tunnel (staging first)

1. Follow [cloudflared/README.md](cloudflared/README.md)
2. Edit `cloudflared/config.yml` with tunnel id
3. Set in `.env`: `ROOTMC_TUNNEL_ID=...`
4. `Start-LocalEdge.ps1` (without `-SkipTunnel`)

## Discord /link without Cloudflare Workers

Two paths (same in-game `/link` code):

1. **DM the RootMC bot** `link ABC123` â€” handled by `discord-link-bot.mjs` (Discord Gateway outbound from this PC â†’ local API). **No Workers / no tunnel required** for completion.
2. **Web OAuth** `/verify` + Discord authorize â€” needs public HTTPS on the API host (tunnel primary or CF fallback). `SITE_URL` / `DISCORD_ROOTMC_OAUTH_REDIRECT_URI` come from wrangler `.dev.vars` (synced from `.env` on start).

Slash command **`/link code:`** is also registered (needs Interactions Endpoint URL on the live API host).

```powershell
# Register /link slash command (once)
cd "D:\.1 Work Stations\RootMC\Web Files\rootmc-realm-api"
node scripts\discord-register-rootmc-commands.mjs
```

## Pre-online sync gate (required before public cutover)

Pull D1, verify Official peer configs, local edge health, and Discord Gateway bot â€” then cut over DNS:

```powershell
cd "D:\.1 Work Stations\RootMC\scripts\local-edge"
powershell -File .\Assert-PreOnlineSync.ps1 -Apply
# Fix any FAIL lines, re-run until exit 0
powershell -File .\Start-LocalEdge.ps1   # includes tunnel when config.yml is filled
powershell -File .\Invoke-EdgeCutover.ps1 -Preference local -Reason preonline_ok
# Production hostnames only when staging validated:
# powershell -File .\Invoke-EdgeCutover.ps1 -Preference local -AllowProduction -Reason preonline_ok
```

`Invoke-EdgeCutover.ps1` refuses local preference until `preonline-ready.json` is fresh (or pass `-Force`).

## Cutover

```powershell
# Staging CNAMEs â†’ tunnel
powershell -File .\Invoke-EdgeCutover.ps1 -Preference local -Reason health_ok

# Production hostnames (explicit)
powershell -File .\Invoke-EdgeCutover.ps1 -Preference local -AllowProduction -Reason health_ok

# Prefer Cloudflare Workers again
powershell -File .\Invoke-EdgeCutover.ps1 -Preference cloudflare -Reason local_offline -AllowProduction
```

CF cron watchdog (`*/10`) flips mirrored preference to `cloudflare` when `ROOTMC_TUNNEL_HEALTH_URL` fails while preference is `local`. Set secret/var:

- `ROOTMC_TUNNEL_HEALTH_URL=https://ava-origin.rootmc.net/health` (or gateway `/__edge/health`)
- Optional: `ROOTMC_EDGE_SIGNING_KEY` for `X-RootMC-Cache-Signature` on cacheable GETs

## D1 replicate

```powershell
powershell -File .\Sync-D1Replica.ps1 -Direction push   # local â†’ remote warm backup
powershell -File .\Sync-D1Replica.ps1 -Direction pull   # catch-up after CF-primary window
```

Treasury/ledger tables are last-write-wins â€” review dumps before production import.

## Map assets

```powershell
powershell -File .\Sync-MapAssets.ps1 -Direction pull
powershell -File .\Sync-MapAssets.ps1 -Direction push
```

Gateway disk-caches map tiles under `cache/` regardless of R2 sync.

## Deploy Worker changes (human)

After pulling these API changes, deploy when CF allows:

```powershell
powershell -File "D:\.1 Work Stations\RootMC\Web Files\rootmc-api\deploy.ps1"
```

New optional secrets/vars: `ROOTMC_EDGE_SIGNING_KEY`, `ROOTMC_TUNNEL_HEALTH_URL`.

## Cache foundations

- Worker: `public-cache.ts` + HMAC hook on cacheable public GETs
- Gateway: disk cache, stale-if-error when origin busy/down, signing headers
- Uncacheable: auth, Discord, heartbeats, economy sync POSTs, treasury writes
