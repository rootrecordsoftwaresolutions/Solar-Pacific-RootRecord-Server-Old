# EcoFlow fast poll + SQL samples (2026-08-03)

Alex `1533895165045903382`: River should be online — keep polling fresh; MySQL/SQL please.

## Shipped

1. **Poll cadence** — EcoFlow + host-site push every **~60s** (`AVA_ECO_POLL_MS`, was buried in 10m watcher → went stale under 3m live gate)
2. **D1 SQL** — `rootmc_ecoflow_samples` table; each telemetry POST also inserts per-SN rows (live + lastKnown). GET `/api/rootmc/host-site/ecoflow/samples?sn=&limit=`
3. **Live rule unchanged** — device-list `online:0` still excluded from live bank (River still `online:0` on EcoFlow cloud as of check — will flip live within one poll when cloud says 1)

## Ops

- Restart Ava poller to pick up 60s cadence
- Deploy `rootmc-api` / realm Worker so D1 sample inserts run in prod
- When River LED is on but list stays 0, EcoFlow cloud lag — not Ava inventing online
