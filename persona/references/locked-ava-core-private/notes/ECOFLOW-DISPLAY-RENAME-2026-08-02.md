# EcoFlow display rename (2026-08-02)

Operator asked to drop casual nicknames for product labels.

| SN | Old label | New label |
|---|---|---|
| `R331ZAB5SG6S2858` | cucumbers | **Delta 2** |
| `R621ZA16XH6K1155` | shackas | **River 2 Pro** |
| `R331ZAB5SG755642` | Delta 2-B | **REMOVED 100%** (Alex 2026-08-03) |

## Runtime
- Display maps: `hostSite.mjs`, `powerTelemetry.mjs`, `opsPowerStatus.mjs` (`NICK_BY_SN` / `snLabel`)
- Alias map: `ECO_NICKNAMES` in `ecoflow.mjs` — product names primary; cucumbers/shackas kept as spoken aliases only
- Removed: `ECO_REMOVED_SNS` / `isEcoRemoved` hard-blocks Delta 2-B from poll + UI

Solar dashboard + status use `powerTelemetry` labels → **Delta 2** / **River 2 Pro** only.
