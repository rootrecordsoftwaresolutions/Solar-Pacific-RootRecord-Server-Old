# Quick reference

One-page lookup for URLs, source repos, and integration touchpoints. For narratives and teaching context, use the [sections index](../../README.md#table-of-contents-sections-013) in the root README.

## Live endpoints

| Surface | URL | Role |
|---------|-----|------|
| Marketing + legal + account shell | [rootrecord.info](https://rootrecord.info) | Cloudflare Pages (`Web/main/`) |
| Primary API | [api.rootrecord.info](https://api.rootrecord.info) | Worker `rootrecord-primary` (auth, weather, business, earn, Solana routes, crons) |
| RootMC site | [rootmc.net](https://rootmc.net/) | SMP hub, verify, shops, market, wiki |
| RootMC API | [api.rootmc.net](https://api.rootmc.net/) | RootMC Discord bot, Minecraft sync, Block Notes cloud |
| Solana Tools (browser) | [solana.rootrecord.info](https://solana.rootrecord.info) | Next.js app ([solana-rootrecord-site](https://github.com/RootRecord/solana-rootrecord-site)) |
| Doc Repo (this knowledge base) | [github.com/RootRecord/Doc-Repo](https://github.com/RootRecord/Doc-Repo) | Markdown only—safe to index |

## Source repositories (typical)

| Area | GitHub / layout | Package manager |
|------|-----------------|-----------------|
| Mobile apps monorepo | `Mobile-Development-2026` / `MonoRepo/Mobile/` | `pnpm` (Capacitor) · Gradle (Kotlin natives) |
| Web Workers + Pages | `Web-Development-2026` / `MonoRepo/Web/` | `npm ci` (Workers) |
| Minecraft plugins | `MonoRepo/Minecraft/` | Gradle (`build-with-server-jdk.bat`) |
| Solana Tools site (canonical) | [`RootRecord/solana-rootrecord-site`](https://github.com/RootRecord/solana-rootrecord-site) | `pnpm` at repo root |

## Integration matrix (at a glance)

| External system | Where it connects | Purpose |
|-----------------|-------------------|---------|
| **Stripe** | Primary + licence Workers (`STRIPE_*`) | Subscriptions, Checkout, billing webhooks |
| **Cloudflare Workers + D1** | `rootrecord-primary` | HTTP API, SQL edge data, scheduled jobs |
| **Cloudflare Pages** | `Web/main` | Static **rootrecord.info** + `functions/` |
| **Firebase (FCM)** | Primary Worker secrets | Push notification delivery |
| **Solana RPC** | Worker env + mobile clients | Reads, custodial/treasury flows where configured |
| **NOAA / weather providers** | Primary Worker weather + crons | Alerts, ingest, caches (`WEATHER_DATA_TTL_SEC`, AccuWeather keys) |
| **Discord (webhooks)** | Worker secrets | Ops alerts, feedback, Solana tooling signals |
| **Resend (optional)** | `RESEND_*` on Worker | Transactional email where enabled |
| **Vercel / forward URL** | `SOLANA_TOOLS_API_FORWARD_URL` | Proxy selected Next-only API routes for Solana hostname |

## `solana.json` (ecosystem discovery)

Repo root **[`solana.json`](../../solana.json)** lists Solana programs/API neighbors, versioned-transaction notes, and explicit **non-use** of Pyth for weather (weather uses conventional ingest via the primary API).

## GitHub repository topics (discovery)

Intended **topic tags** for this Doc Repo (mirror on the repo **About → Topics**):  
`solana`, `web3`, `infrastructure`, `identity`, `developer-tools`, `documentation`, `cloudflare`, `android`, `api`.

## Related reading

- [Integrations (detail)](../03-platform/integrations.md)
- [Domains & URLs](../03-platform/domains-and-urls.md)
- [Glossary](../01-introduction/glossary.md)
