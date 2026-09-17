# Glossary

Short definitions for terms that appear across RootRecord docs and repos.

| Term | Meaning |
|------|--------|
| **RootRecord** | The product/brand and associated APIs, apps, and websites described in this documentation. |
| **RootRecord Identity** | Account, sessions, and entitlements across apps, plus Solana-facing surfaces (e.g. linked wallets) where the product exposes them—see [api.rootrecord.info](https://api.rootrecord.info). |
| **RootRecord Weather** | The **Weather Manager** product line: forecasts, hazard alerts, and API-backed environmental data—see [Weather Manager](../02-products/weather-manager.md) and the primary API. |
| **Primary API** | Production HTTP API at [https://api.rootrecord.info](https://api.rootrecord.info), implemented by the Cloudflare Worker **`rootrecord-primary`**. |
| **Licence / license Worker** | Cloudflare Worker (`rootrecord-license`) used for legacy/companion licensing flows; marketing **account.html** may call a configured API base from site config. |
| **D1** | Cloudflare’s SQLite-backed edge database. The primary Worker uses D1 for many persisted structures (including business-owned rows, observability, etc.—see platform docs). |
| **JWT** | JSON Web Token. Mobile apps typically store a bearer token after login and send it on API requests. |
| **Entitlement** | Server-side view of what a user’s plan unlocks (features, apps). Clients call endpoints like `/api/auth/entitlement` (exact paths may evolve—check app code). |
| **Account Hub** | Mobile app: central place for account, subscription links, security, and shortcuts into other RootRecord apps. |
| **Business Manager (mobile)** | Android-first Capacitor app for business operations; cloud row storage on primary API (`bm_owned_row` family of routes/data). |
| **Weather Manager** | Weather and alerts app (Windows and Android in the portfolio). |
| **Volcano alert codes (AccuWeather feed)** | Abbreviated types the Worker maps to readable labels—**VOW** (Volcano Warning), **VOWS** (Volcano Watch), **VOA** (Volcano Activity)—when those products appear in the aggregated alerts response. See [Kīlauea & volcano alerts](../10-deep-dives/kilauea-and-volcano-alerts.md). |
| **Kīlauea / Hawaiʻi volcano context** | Educational overview of how volcanic notices can surface alongside other alerts in Weather Manager—same **`alerts`** bundle field—not a separate USGS-only integration. See [Kīlauea & volcano alerts](../10-deep-dives/kilauea-and-volcano-alerts.md). |
| **Token Manager** | Mobile Solana-oriented app; pairs with external wallets; non-custodial by design for keys. |
| **Block Notes** | Native Android Minecraft companion (`com.rootrecord.rootmc`); offline Room storage with optional `rootmc-realm-api` sync. |
| **RootRecord Realm** | Web hub at `/realm/` plus Paper plugin linking Minecraft UUIDs to Root Record accounts. |
| **RootStat / RootMC plugin** | Paper plugin commands (`/rootstat`, `/rootmc`); production ships unified **RootMC** jar only. |
| **API shard** | Per-product Cloudflare Worker (e.g. `rootmc-realm-api`) with its own router and D1 bindings. |
| **Solana Tools site** | Public Next.js app in repo [**RootRecord/solana-rootrecord-site**](https://github.com/RootRecord/solana-rootrecord-site), deployed from that repo’s root. Live at [solana.rootrecord.info](https://solana.rootrecord.info). Not developed under arbitrary copies inside `Web/solana/`. |
| **Pages** | Cloudflare Pages—static site hosting. **`Web/main/`** is the rootrecord.info marketing site. |
| **Worker** | Cloudflare Worker—a serverless V8 isolate at the edge running the API routers. |
| **pnpm / npm** | Package managers. Mobile monorepo uses **pnpm**; many Workers use **npm ci**. |
| **Capacitor** | Wrapper that ships a web frontend inside native shells (e.g. Android) with plugins for native APIs. |
| **rr_earn_*** | Naming prefix for earn/rewards-related tables or routes in the primary Worker (e.g. summaries). |

When in doubt, **search the codebase** for the exact symbol or path—this glossary is for orientation, not exhaustiveness.
