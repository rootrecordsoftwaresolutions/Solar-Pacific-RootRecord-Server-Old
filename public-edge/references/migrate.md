# Migrate `public-edge` into this skill

Status: **mapped topic**. Function desks for inbox/stripe/vercel already moved. Websites live in `avaivy-cloud`, `rootrecord-online`, `alexrs94-site`, `holding`. Workers live in `cloudflare-workers` (`Ava-Core/workers`). Do not copy `.env`.

When we cut this topic over:

1. Put runners in `scripts/` (Ollama skill shape).
2. Keep `SKILL.md` as the model instructions.
3. Point scheduler/systemd at the new scripts.
4. Leave Media, `.env`, sqlite, and jsonl history where they are.
5. Refresh: `python3 ~/.ollama/skills/ecosystem-index/scripts/refresh-all.py`

## Current live files (from INDEX)

# Desk — public-edge

Generated 2026-09-16T01:29:41-10:00. This folder is the ops desk for the topic.

Open **this skill directory**. `desk/src` and `desk/ops` are symlinks to the
Python/shell that actually runs (scheduler still imports Ava-Core / Core Ops).
`DAILY.md` is the processed hybrid-style summary. `CURRENT.md` is the map.
Do not invent watts, SOC, or player counts. Do not open `.env`.

| In this desk | Live path |
| --- | --- |
| `desk/live/admob.py` | `/home/rootrecord/.ollama/skills/admob-eod/scripts/admob.py` |
| `desk/live/adsense.py` | `/home/rootrecord/.ollama/skills/adsense-eod/scripts/adsense.py` |
| `desk/live/api_ledger.py` | `/home/rootrecord/.ollama/skills/api-prices/scripts/api_ledger.py` |
| `desk/live/finance_desk.py` | `/home/rootrecord/.ollama/skills/finance-desk/scripts/finance_desk.py` |
| `desk/live/heartbeat.py` | `/home/rootrecord/.ollama/skills/heartbeat/scripts/heartbeat.py` |
| `desk/live/inbox.py` | `/home/rootrecord/.ollama/skills/inbox/scripts/inbox.py` |
| `desk/live/job.py` | `/home/rootrecord/.ollama/skills/inbox-drain/scripts/job.py` |
| `desk/live/offline_inbox.py` | `/home/rootrecord/.ollama/skills/inbox-drain/scripts/offline_inbox.py` |
| `desk/live/public_chat.py` | `/home/rootrecord/.ollama/skills/public-chat/scripts/public_chat.py` |
| `desk/live/public_finance.py` | `/home/rootrecord/.ollama/skills/public-finance/scripts/public_finance.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/vercel-builds/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/stripe-poll/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/api-prices/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/economy-brief/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/adsense-eod/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/admob-eod/scripts/job.py` |
| `desk/live/scripts-job.py` | `/home/rootrecord/.ollama/skills/api-prices-boot/scripts/job.py` |
| `desk/live/site_backgrounds.py` | `/home/rootrecord/.ollama/skills/site-backgrounds/scripts/site_backgrounds.py` |
| `desk/live/site_ops.py` | `/home/rootrecord/.ollama/skills/site-ops/scripts/site_ops.py` |
| `desk/live/stripe_poll.py` | `/home/rootrecord/.ollama/skills/stripe-poll/scripts/stripe_poll.py` |
| `desk/live/subscribers.py` | `/home/rootrecord/.ollama/skills/subscribers/scripts/subscribers.py` |
| `desk/live/vercel_builds.py` | `/home/rootrecord/.ollama/skills/vercel-builds/scripts/vercel_builds.py` |
| `desk/src/apps/core/crons/always_on/inbox_drain.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/crons/always_on/inbox_drain.py` |
| `desk/src/apps/core/crons/always_on/stripe_poll.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/crons/always_on/stripe_poll.py` |
| `desk/src/apps/core/crons/always_on/vercel_builds.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/crons/always_on/vercel_builds.py` |
| `desk/src/apps/core/heartbeat.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/heartbeat.py` |
| `desk/src/apps/core/routes/local_site.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/routes/local_site.py` |
| `desk/src/apps/core/routes/public_site.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/routes/public_site.py` |
| `desk/src/apps/core/services/admob.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/admob.py` |
| `desk/src/apps/core/services/adsense.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/adsense.py` |
| `desk/src/apps/core/services/finance_desk.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/finance_desk.py` |
| `desk/src/apps/core/services/offline_inbox.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/offline_inbox.py` |
| `desk/src/apps/core/services/public_finance.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/public_finance.py` |
| `desk/src/apps/core/services/site_ops.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/site_ops.py` |
| `desk/src/apps/core/services/stripe_poll.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/stripe_poll.py` |
| `desk/src/apps/core/services/vercel_builds.py` | `/home/rootrecord/.ollama/skills/origin/apps/core/services/vercel_builds.py` |
| `desk/src/packages/workers/kilauea-worker.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/kilauea-worker.ts` |
| `desk/src/packages/workers/src/ava-api/worker.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/ava-api/worker.ts` |
| `desk/src/packages/workers/src/rootmc-api/ava-cron-kick.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/rootmc-api/ava-cron-kick.ts` |
| `desk/src/packages/workers/src/rootmc-api/worker.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/rootmc-api/worker.ts` |
| `desk/src/packages/workers/src/rootrecord-api/worker.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/rootrecord-api/worker.ts` |
| `desk/src/packages/workers/src/rootrecord-cloud/worker.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/rootrecord-cloud/worker.ts` |
| `desk/src/packages/workers/src/shared/ecoflow.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/ecoflow.ts` |
| `desk/src/packages/workers/src/shared/feedbackPage.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/feedbackPage.ts` |
| `desk/src/packages/workers/src/shared/heartbeat.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/heartbeat.ts` |
| `desk/src/packages/workers/src/shared/maintenancePage.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/maintenancePage.ts` |
| `desk/src/packages/workers/src/shared/offlineInbox.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/offlineInbox.ts` |
| `desk/src/packages/workers/src/shared/proxy.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/proxy.ts` |
| `desk/src/packages/workers/src/shared/publicPaths.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/publicPaths.ts` |
| `desk/src/packages/workers/src/shared/statusPage.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/statusPage.ts` |
| `desk/src/packages/workers/src/shared/types.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/types.ts` |
| `desk/src/packages/workers/src/shared/uptime.ts` | `/home/rootrecord/.ollama/skills/origin/packages/workers/src/shared/uptime.ts` |
| `desk/src/packages/workers/wrangler.ava-api.toml` | `/home/rootrecord/.ollama/skills/origin/packages/workers/wrangler.ava-api.toml` |
| `desk/src/packages/workers/wrangler.rootrecord-cloud.toml` | `/home/rootrecord/.ollama/skills/origin/packages/workers/wrangler.rootrecord-cloud.toml` |
| `desk/src/scripts/deploy-public-sites.sh` | `/home/rootrecord/.ollama/skills/origin/scripts/deploy-public-sites.sh` |
| `desk/src/scripts/site-update.py` | `/home/rootrecord/.ollama/skills/origin/scripts/site-update.py` |
| `desk/src/scripts/sync-blogs.py` | `/home/rootrecord/.ollama/skills/origin/scripts/sync-blogs.py` |


