# .env preservation — intact on E + copy on device

**Rule:** Every secrets file stays **intact** on SATA/E (and D while Windows lives).  
A **copy** is placed on the Ubuntu **SSD device** for runtime. Never commit. Never print values in chat/logs.

## Inventory (Work Stations)

| Relative path | Role |
|---------------|------|
| `.credentials/.env` | Fallback secrets pack |
| `.credentials/solana.env` | Solana-related secrets |
| `RootMC/.env` | **Primary** RootMC / Ava / CF / Discord / JWT |
| `RootMC/Plugin Building/Minecraft/.env` | Plugin-local secrets if used |
| `RootMC/scripts/local-edge/state/mysql-bootstrap.env` | Local MySQL bootstrap |
| `RootMC/Server Handoffs/*/plugins/RootMC/r2.env` | R2 creds in handoffs |
| `RootMC/Server Handoffs/3…/.dev-server-identity.env` | Dev identity |
| `*.env.example` | Templates only — not secrets |

Live Backups under `Server Live Backups/` may contain older copies — treat as archive; do not “fix” from them unless asked.

## Intact = do not destroy

1. **E:\.1 Work Stations\…** — migration / Linux Cursor source of truth  
2. **D:\.1 Work Stations\…** — Windows live (until cutover)  
3. **E:\windows backup\…** — backup mirror  

Device copies are **additional**. Updating secrets: edit E (or D), then re-run sync to device.

## Device copy layout (SSD)

```
/srv/rootmc/.env                          ← from RootMC/.env
/srv/rootmc/.credentials.env              ← from .credentials/.env
/srv/rootmc/.credentials.solana.env       ← from .credentials/solana.env
/srv/rootmc/secrets/                      ← full relative mirror of all matched .env files
  .credentials/.env
  RootMC/.env
  RootMC/Plugin Building/Minecraft/.env
  …
```

Permissions: `chmod 600` files · `chmod 700` `secrets/` · owner = provision user.

`ROOTMC_ENV_FILE=/srv/rootmc/.env` for Ava / deploy scripts.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/sync-env-to-device.sh` | On Ubuntu: copy from mounted E → `/srv/rootmc` (source untouched) |
| `scripts/ensure-env-on-e.ps1` | On Windows: ensure E has every D `.env` (copy, not move) |
| Wired into `ubuntu-provision-ecosystem.sh` | Calls sync at end unless `SKIP_ENV_SYNC=1` |

## Verify (no secret dump)

```bash
# On Ubuntu — names + sizes only
find /srv/rootmc/secrets -type f -name '*.env*' -printf '%p %s\n'
test -f /srv/rootmc/.env && echo "primary .env OK bytes=$(wc -c </srv/rootmc/.env)"
# Compare byte size to E original (should match)
wc -c "/mnt/e/.1 Work Stations/RootMC/.env" /srv/rootmc/.env
```

## Hard no

- Do not `mv` `.env` off E  
- Do not put `.env` in git  
- Do not paste `.env` into Discord/Slack/Cursor chat  
- Do not chmod 644 secrets on a multi-user box
