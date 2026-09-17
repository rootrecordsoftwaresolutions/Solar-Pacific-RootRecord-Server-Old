# Host site public copy — no city/state (2026-08-02)

Alex lock: **do not name Mountain View / Hawaii** in public host-site reports (Discord, hourlies, Ava voice).

## Public labels
- Label: `Root Server host (Starlink / solar)`
- Locale string: `Host site`
- Weather source line: `NWS (local point)` — never city/state
- Coords stay in private JSON for NWS point queries only

## Scrub
`scrub.mjs` flattens `Mountain View` / `Hawaii Mountain View` in outbound Ava text.

## Hour recap automation
- Module: `src/hourRecap.mjs`
- Poller tick ~hourly → `#updates`
- CLI: `node scripts/hour-recap.mjs --force`
