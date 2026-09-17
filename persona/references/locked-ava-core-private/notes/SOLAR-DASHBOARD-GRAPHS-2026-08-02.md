# Solar dashboard graphs + tracking (2026-08-02)

Alex (#updates): "On the website, build a page for all your solar stuff" + follow-ups for graphs, CPU, averages/totals, sunrise, online-since, offline states, Ava Core tunnel links.

## Live
- `https://ava.rootmc.net/solar` (tunnel → :8787) · local `http://127.0.0.1:8787/solar`
- API: `/api/solar?hours=8`

## Code
- `src/solarPage.mjs` — SVG charts + KPI/stat panels (Ava green visual language)
- `src/powerTelemetry.mjs` — live EcoFlow + host-metrics + NWS pack (no invented numbers)
- `src/ecoflow.mjs` `loadEcoMinuteSeries` · `src/hostMetrics.mjs` `loadHostMetricsMinuteSeries`
- `src/hostSite.mjs` — NWS `astronomicalData` sunrise/sunset on weather pack
- `src/server.mjs` — `/solar`, `/power`, `/api/solar`
- Status page footer/sub links to solar + ava.rootmc.net
- Heartbeat `bootAt` + liveness `parentStartedAt` for online-since

Wh totals = sum of minute watt samples / 60 (honest sample-window energy).

— Ava
