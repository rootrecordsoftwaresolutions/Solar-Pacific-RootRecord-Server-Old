# Tomorrow — Ava wiki & status home (2026-08-06)

**Locked direction (Alex):** Ava is the core of everything. Root Record hosts her public knowledge home.

## Ship shape

| URL | Role |
|-----|------|
| `https://rootrecord.info/ava/` | Full wiki — every surface, brain, cron, data path she touches |
| `https://rootrecord.info/ava/status` | Live ops board (solar/host/weather) — former `ava.rootmc.net` home |
| `https://ava.rootmc.net/` | Keep as alias during soft-ack; eventually redirect → `/ava/status` |

## Source

`/home/ava-core/ava/workstations/projects/rootrecord-ava/`

Worker `rootrecord-ava` routes `rootrecord.info/ava*`.

## Follow-ups for tomorrow

1. Confirm Worker route wins over Pages on apex (`rootrecord-website`) for `/ava*`.
2. When Ava systemd is healthy, verify `/ava/status` KPIs + `/ava/status/api/solar`.
3. Add wiki link from rootrecord.info homepage product grid (needs main site source on E or Pages edit).
4. Update Discord/Slack pointers that still say only `ava.rootmc.net`.
5. Optional: `ava.rootmc.net` → 302 to `rootrecord.info/ava/status`.
6. Expand wiki pages from dig notes (PROP index, army, appearance) as she grows.
7. Logging hardening (cron/API into flight recorder) — separate track from wiki.

## Why Root Record domain

She's not a RootMC-only bot anymore — she runs RootMC **and** Root Record. The wiki lives on the product mothership; the game domain keeps play/API/map.


## Shipped tonight (2026-08-05 HST)

| URL | Status |
|-----|--------|
| https://rootrecord.info/ava/ | **Live** wiki hub (Worker `rootrecord-ava`) |
| https://rootrecord.info/ava/core.html | **Live** (and other atlas pages) |
| https://rootrecord.info/ava/status | Proxies Ava board — needs `ava-ivy` up |
| https://ava.rootrecord.info/ | **Live** subdomain alias (same Worker) |
| https://ava.rootmc.net/ | Legacy status alias (keep during soft-ack) |

Source: `/home/ava-core/ava/workstations/projects/rootrecord-ava/`
