# Host site in hourly snapshots (2026-08-02)

Alex ask (#updates): include **solar**, **localized weather** (RootRecord/NWS), **storm/hazards**, for **Hawaii Mountain View Starlink/solar server**.

## Ship
- `data/host-site.json` - Mountain View, HI coords
- `src/hostSite.mjs` - EcoFlow solar + NWS forecast/alerts; push telemetry
- Worker `rootmc-host-site.ts` - POST/GET telemetry + NWS section
- `rootmc-live-economy-status.ts` - Host site block in hourly Discord post
- Ava poller syncs telemetry after EcoFlow refresh

## Deploy note
Human: `Web Files/rootmc-api/deploy.ps1` so Worker route + hourly section go live.
Until then Ava still builds/pushes telemetry locally; NWS works without deploy.

Weather uses **NWS** (same hazard rail RootRecord Weather Manager). RootRecord weather shard is up; public current routes not exposed on account API - NWS is the durable path.

- Ava
