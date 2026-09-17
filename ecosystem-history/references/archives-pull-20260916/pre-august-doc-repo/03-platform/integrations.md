# Integrations & external systems

How RootRecord’s platform talks to the outside world. This is a **map**, not a secret catalog—keys live only in Cloudflare, stores, and gitignored env files.

## Cloudflare (core platform)

| Piece | Role |
|-------|------|
| **Workers** | `rootrecord-primary` serves **api.rootrecord.info**; companion Workers handle licence, app-build requests, Solana tx helpers. |
| **D1** | Relational edge database for sessions, business rows, weather cache, observability, and program-specific tables. |
| **Pages** | **rootrecord.info** static site + Functions (`/api/site-config`, etc.). |
| **Secrets** | JWT, Stripe, FCM JSON, wallet encryption, Discord URLs, admin push keys—set via `wrangler secret put` or dashboard. |

## Payments & entitlements

| System | Usage |
|--------|--------|
| **Stripe** | Checkout sessions, recurring prices, customer portal handoffs; entitlement refresh on **api.rootrecord.info** and licence Worker routes as deployed. |

Clients must not embed secret keys; **server-side** Workers hold `STRIPE_SECRET_KEY`.

## Identity & messaging

| System | Usage |
|--------|--------|
| **Firebase Cloud Messaging** | Device push tokens; Worker routes register prefs + targeted or broadcast sends when configured. |
| **Email (e.g. Resend)** | Optional verification or account email flows via `RESEND_*` when enabled. |

## Solana & web3

| Surface | Usage |
|---------|--------|
| **Solana RPC** | RPC URL in Worker env for custodial/cron paths; mobile/web clients use network-appropriate RPC for wallet and SPL reads. |
| **Wallets (user)** | Phantom-style flows; Token Manager stays **non-custodial** for seeds. |
| **Treasury / custodial programs** | Separate keys and crons—high-privilege; documented in code and ops runbooks, not here. |
| **Solana Tools Next.js** | Hosted separately (e.g. Vercel); primary Worker may **forward** selected `/api/*` paths via `SOLANA_TOOLS_API_FORWARD_URL`. |

Named **2026 ecosystem neighbors** (e.g. **Jupiter** price/Jupiter Wallet paths, **Raydium** CPMM, **Pinata** IPFS via server routes, **Helius / QuickNode**-class RPC URLs, and explicit **non-use** of **Pyth** for weather) are tabulated in the **root README** under *Ecosystem tags (related-tool discovery)* and in **`solana.json`**—not duplicated here in full.

## Weather & hazard data

| Source | Role |
|--------|------|
| **NOAA / NWS** | Active **point** alerts (`api.weather.gov`) feed the dashboard **`alerts`** field when AccuWeather is not used for that path—including coordinated volcanic ash or related products where issued for the location. |
| **Commercial providers (e.g. AccuWeather)** | Keys via Worker secrets; TTL reuse reduces duplicate calls. The alerts endpoint can surface **government-style volcano products** (e.g. **VOW** / **VOWS** / **VOA** codes) merged into AccuWeather’s schema; see [Kīlauea & volcano alerts](../10-deep-dives/kilauea-and-volcano-alerts.md). |
| **USGS (indirect)** | RootRecord does **not** query USGS APIs as a dedicated volcano-only pipe—volcano items appear when **NWS** or **aggregated commercial alert** pathways include them. |

## Ops & feedback

| Channel | Role |
|---------|--------|
| **Discord webhooks** | Feedback route, Solana tooling notifications, token mint hooks—internal visibility. |
| **Discord bot (Global Updater)** | Product announcements, Kīlauea reports—`DISCORD_BOT_TOKEN` in ops env. |

## Minecraft & Realm

| Piece | Role |
|-------|------|
| **Paper plugin (RootMC)** | Server heartbeat, player linking headers (`X-RootStat-Server-Id`), MySQL cache for McMMO/playtime |
| **MySQL** | Optional on-game-server DB shared with McMMO/Vault; plugin pushes aggregates to D1 via API |
| **D1 (`rootmc-api`)** | Linked players, Realm social, server registry, account snapshots |

## Client stacks (integration boundary)

| Stack | Notes |
|-------|--------|
| **Android + Capacitor** | Mobile apps call **HTTPS APIs** only; deep links use custom URL schemes where configured. |
| **React + Tailwind** | Shared UI patterns across apps; not a separate hosted “integration.” |

## Related reading

- [API overview](api-overview.md)
- [Quick reference](../09-reference/quick-reference.md)
- [Solana Tools site](../05-solana/solana-tools-site.md)
