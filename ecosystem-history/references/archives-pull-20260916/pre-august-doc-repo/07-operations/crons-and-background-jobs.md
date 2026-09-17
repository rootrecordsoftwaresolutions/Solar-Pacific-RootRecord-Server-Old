# Crons & background jobs

The **`rootrecord-primary`** Worker schedules work via Cloudflare **cron triggers**. This page summarizes **intent**; schedules live in `wrangler.toml` / Worker config.

## Weather / NOAA ingest

Periodic jobs refresh alert and hazard pipelines so mobile clients read relatively fresh data without hammering upstream providers on every tap.

## Inactive account cleanup

A daily-style job purges **abandoned** accounts past configurable inactivity thresholds (`ABANDONED_ACCOUNT_INACTIVITY_DAYS`).

## Observability pruning

HTTP error events get trimmed so D1 doesn’t grow without bound.

## Solana treasury triggers

At selected UTC minutes, the Worker may POST to **`rootrecord-solana-tx`** paths for liquidity / treasury maintenance—guarded by admin secrets.

## Custodial earn payouts

Separate cron paths handle **RRTT custodial** payout flows when configured (mint, treasury key, RPC URL).

## Operational guidance

- Watch Worker logs in Cloudflare dashboard when enabling new crons.
- Ensure secrets exist before schedules fire— silent no-ops often mean missing env.

## Related reading

- [observability.md](observability.md)
- [../05-solana/solana-ecosystem.md](../05-solana/solana-ecosystem.md)
