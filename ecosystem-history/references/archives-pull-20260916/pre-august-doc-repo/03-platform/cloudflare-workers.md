# Cloudflare Workers in the RootRecord stack

**Cloudflare Workers** are serverless functions that run on Cloudflare’s edge network. RootRecord uses them for **APIs**, **background crons**, and **selective proxying** to other origins (e.g. Next.js API routes for Solana Tools).

## Primary API Worker (`rootrecord-primary`)

- **URL:** `https://api.rootrecord.info`
- **Database:** **D1** (SQLite at the edge) for durable structures.
- **Secrets:** JWT signing keys, Stripe keys, Firebase for push, wallet encryption material, Discord webhooks, admin push secrets—**never committed**.

### Scheduled jobs

The Worker defines **cron triggers** for:

- NOAA / weather ingest cadence
- Inactive account cleanup + observability pruning
- Solana treasury-related triggers that POST into `rootrecord-solana-tx` at specific UTC minutes
- Custodial payout flows where configured

See [../07-operations/crons-and-background-jobs.md](../07-operations/crons-and-background-jobs.md).

## Licence Worker (`rootrecord-license`)

Companion to account/licensing flows. Marketing site `account.js` expects particular DOM ids and API behavior—preserve contracts when editing.

## App build Worker (`rootrecord-app-build`)

Accepts build/support requests originating from the marketing form (`/api/app-build-request` on Pages proxies here).

## Solana transaction Worker (`rootrecord-solana-tx`)

Invoked by primary Worker HTTP calls for treasury-style operations. Treat this as **high privilege**: locked down with admin secrets and non-public routes.

## Shared libraries (`Web/cloudflare/shared`)

Password verification, billing helpers, app association utilities—**reuse** instead of duplicating logic across Workers.

## Developer workflow

1. `npm ci` in the Worker package.
2. `.dev.vars` from `.dev.vars.example` for local emulation.
3. `wrangler deploy` with secrets configured in the Cloudflare dashboard or via `wrangler secret put`.

Never paste production secrets into this Doc Repo.
