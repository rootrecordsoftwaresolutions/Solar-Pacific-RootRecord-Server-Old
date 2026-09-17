# Weather Manager

**Weather Manager** delivers **weather intelligence and hazard awareness** for users who want clarity without juggling ten browser tabs. It exists in **Windows and Android** forms in the RootRecord portfolio.

## What problem it solves

- **Timely alerts** — NOAA and related feeds power alert-style experiences (implementation details live in the Worker `weather` modules and cron jobs).
- **Current + forecast** — Grid-based or provider-backed weather content depending on configuration (AccuWeather keys, TTL caches, etc.).
- **Environmental signals** — The primary Worker also bundles routes for earthquakes, wildfires, cyclones, tsunami bulletins, and related dashboards—useful for “big picture” hazard tracking. **Volcano-related alerts** (for example notices affecting **Kīlauea** on Hawaiʻi Island) appear through the same **`alerts`** field as other hazards when upstream feeds include them—see [Kīlauea & volcano alerts](../10-deep-dives/kilauea-and-volcano-alerts.md).

## How it talks to the cloud

Mobile and desktop clients call **`https://api.rootrecord.info`** for authenticated weather routes. The Worker normalizes CORS, caches aggressively where safe, and logs requests for observability.

## Earn integration

Weather Manager participates in the **`rr_earn_*`** rewards-style surface alongside other apps (see primary Worker earn routes). Treat earn as **programmatic**—exact rates and eligibility belong in product/legal copy, not here.

## Teaching tips

Explain **location permission** in plain language: it powers relevant grids and alerts. Explain **background constraints** on Android honestly (OS battery policies affect freshness).

## Related reading

- Crons for ingest jobs: [../07-operations/crons-and-background-jobs.md](../07-operations/crons-and-background-jobs.md)
- API overview: [../03-platform/api-overview.md](../03-platform/api-overview.md)
- Kīlauea & volcano alerts (public): [../10-deep-dives/kilauea-and-volcano-alerts.md](../10-deep-dives/kilauea-and-volcano-alerts.md)
