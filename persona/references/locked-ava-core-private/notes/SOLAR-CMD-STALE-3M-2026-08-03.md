# /solar + EcoFlow 3-minute live stale (2026-08-03)

Alex asks:
- `1533888831412109574` — `/solar` on all Discord channels + in-game for host power/weather
- `1533890248252330175` — River 2 Pro showed live watts while offline; treat data older than 3 minutes as unusable for live calc

## Shipped

### EcoFlow live rules
- `ECO_STALE_MS` = **3 minutes** (`ecoflow.mjs`, `powerTelemetry`, `hostSite`, `rootmc-host-site.ts`)
- Device-list `online: 0` → excluded from live bank / solar / boards (quota cache ignored)
- Hard-removed SN (Delta 2-B) scrubbed from snapshots

### Discord `/solar`
- Text ` /solar` in any RootMC guild channel (gateway bypasses watch allowlist)
- Guild slash `/solar` registered at Ava boot
- Reply = HI Pacific Solar Root Server power + NWS weather board

### In-game `/solar`
- Root-Ava-Core **1.8.5** — `SolarCommand` → `GET /api/rootmc/host-site/telemetry`
- Stage jar to Claims / Towny / Test handoffs after build

## Ops
- Restart Ava poller to pick up slash + stale rules
- FileZilla upload `root-ava-core-1.8.5` + Shockbyte restart for in-game `/solar`
- API Worker deploy for 3m stale on mining multiplier (when human deploys)
