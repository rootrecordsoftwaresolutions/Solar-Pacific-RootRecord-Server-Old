# Weather

Standalone local Weather processor for NWS forecasts and Hawaii alerts.

## Ownership

- Implementation: `C:\Users\rootr\RootRecord Core Ops\Weather\weather.py`
- AVA boundary: `ava\apps\core\services\weather.py`
- Scheduler adapter: `ava\apps\core\crons\since_last_fire\noaa.py`
- Generated reports: `C:\Users\rootr\RootRecord Core Ops\Reports\YYYY\Month\Month Dth, YYYY`

Weather owns NWS forecast retrieval, active alert formatting, hourly report
writes, source hashing, and critical-alert publication/queue behavior. It does
not own Kilauea, EcoFlow, Hybrid Tracking Reports, or unrelated report families.

## Daily Path

The current folder convention is preserved:

```text
Reports\2026\September\September 7th, 2026\nws-weather-2026-09-07T17.md
```

Readers use `apps.core.services.reports.latest_report("nws-weather-*.md")`,
which now searches the daily Weather tree. Existing UTC-stamped files are
placed by their converted HST date, so a `03:00Z` report belongs to the prior
Hawaii calendar day when appropriate. Unmigrated report families still use
their existing flat root until their own migration phase.

## Four-operation handoff

| Operation | Weather behavior |
|---|---|
| Local AVA Core and Desk | Fetches NWS data, writes local reports, schedules hourly work. |
| Public VPS and Nodes | Receives future approved report artifacts or APIs; does not invent weather facts. |
| Vercel Site Delivery | Consumes approved weather/site artifacts only. |
| Cloudflare Edge and Fallback | Routes public weather surfaces and fallback behavior; local reports remain authoritative. |
