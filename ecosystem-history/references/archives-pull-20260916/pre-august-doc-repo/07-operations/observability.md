# Observability

RootRecord’s primary Worker logs structured JSON for HTTP requests and persists **error samples** to D1 for later inspection.

## Request logging

Successful and failed requests produce compact JSON lines including latency—useful when correlating user reports with Worker dashboards.

## Error persistence

When responses indicate failures, events may be stored (with careful truncation) for debugging **without** leaking secrets.

## Admin read routes

Privileged routes can expose recent HTTP error events to operators—guarded by secrets (`verifyWorkerOpsAdmin` patterns).

## What this does not replace

- **Product analytics** (funnels) — use explicit telemetry if required.
- **Long-term BI** — export to a warehouse if needed.

## Related reading

- [crons-and-background-jobs.md](crons-and-background-jobs.md)
