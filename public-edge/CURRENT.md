# Public edge and workers — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `heartbeat` — CF heartbeat writer — interval seconds=60
- `api-prices` — Public API price catalog — cron hour=10, minute=25 → `api_prices.run`
- `economy-brief` — Economy brief — cron hour=15, minute=0 → `economy_brief.run`
- `adsense-eod` — AdSense end-of-day report — cron hour=21, minute=0
- `admob-eod` — AdMob end-of-day report — cron hour=21, minute=5
- `inbox-drain` — CF offline inbox → local — interval minutes=5 → `inbox_drain.run`
- `stripe-poll` — Stripe finance snapshot — interval minutes=30 → `stripe_poll.run`
- `vercel-builds` — Vercel build logs → docs — interval minutes=5 → `vercel_builds.run`

## Source files + top-level functions

- `workers/src` — missing
- `workers/kilauea-worker.ts` — missing
- `workers/wrangler.ava-api.toml` — missing
- `workers/wrangler.rootrecord-cloud.toml` — missing
- `sites/avaivy-cloud` — missing
- `sites/rootrecord-online` — missing
- `sites/alexrs94-site` — missing
- `sites/holding` — missing
- `apps/core/routes/public_site.py` — missing
- `apps/core/routes/local_site.py` — missing
- `apps/core/heartbeat.py` — missing
- `apps/core/crons/always_on/inbox_drain.py` — missing
- `apps/core/crons/always_on/vercel_builds.py` — missing
- `apps/core/crons/always_on/stripe_poll.py` — missing
- `apps/core/services/offline_inbox.py` — missing
- `apps/core/services/vercel_builds.py` — missing
- `apps/core/services/stripe_poll.py` — missing
- `apps/core/services/public_finance.py` — missing
- `apps/core/services/finance_desk.py` — missing
- `apps/core/services/adsense.py` — missing
- `apps/core/services/admob.py` — missing
- `apps/core/services/site_ops.py` — missing
- `sites/avaivy-cloud/package.json` — missing
- `sites/rootrecord-online/package.json` — missing

