# Troubleshooting playbook

## “I can’t log in”

1. Confirm **`api.rootrecord.info`** reachable from device browser.
2. Check Cloudflare Worker logs for **401/403** spikes.
3. Verify JWT secret wasn’t rotated without client refresh guidance.

## “Weather data is stale”

1. Confirm cron schedules executed (Worker logs).
2. Check provider keys (`ACCUWEATHER_API_KEY`) and TTL settings.
3. Ask user for **location permission** status on Android.

## “Business data missing”

1. Verify user id matches between devices (same account email).
2. Inspect D1 rows for that user’s business scope—avoid manual edits without backup.

## “Solana transaction failed”

1. Read exact RPC error—insufficient SOL for fees vs slippage vs program rejection.
2. Verify network (mainnet vs devnet) matches user expectation.

## “Solana site didn’t update”

1. Confirm push landed on **`RootRecord/solana-rootrecord-site`** `main`.
2. Check Vercel deployment for that commit hash.

## Escalation

If incident is **security**-shaped (key leak), rotate secrets **first**, then write the postmortem.

## Related reading

- [../07-operations/observability.md](../07-operations/observability.md)
