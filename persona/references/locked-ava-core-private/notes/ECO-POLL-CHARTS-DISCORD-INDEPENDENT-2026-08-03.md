# EcoFlow poll + charts — Discord-independent (2026-08-03)

- EcoFlow refresh + host-site/D1 push now runs on ``startEcoPollLoop`` (~60s), same independence as host-metrics CPU sampler. Discord disconnect / break / slow REST tick must not starve solar.
- Dashboard densifies daytime minutes (HST 06–20) with ≤45m carry; overnight gaps stay null (honest).
- Chart area fills are segment-based — no more giant triangles across null stretches.
- ``hydrateEcoMinutesFromD1`` pulls ``/api/rootmc/host-site/ecoflow/samples?since=`` into local minute buckets when Ava missed polls.
- Worker: ``since`` query + limit up to 2000 on ecoflow samples GET (deploy rootmc-api to pick up).
