# Product family overview

RootRecord’s **shipping surface** today spans Android-first mobile apps, optional Windows clients for some lines, a marketing site, and browser-accessible Solana tooling.

## Product matrix

| Product | Role | Typical user |
|---------|------|----------------|
| **Business Manager** | Operate a small business: time, money, clients, inventory, scheduling, reports | Owner-operator, freelancer |
| **Weather Manager** | Weather, alerts, environmental awareness | Homeowner, site operator, weather-aware professional |
| **Token Manager** | Mobile Solana wallet UX (non-custodial; Phantom-style flows) | Solana user |
| **Account Hub** | Account, subscription entry points, security, app shortcuts | Any returning RootRecord customer |
| **Root Goals** | Guest-first goal planning, Grok AI plans, Actions/Suggestions, public share | Anyone setting personal or project goals |
| **Block Notes** | Offline-first Minecraft notes, coords, maps, build plans; optional Realm cloud | Survival/technical players, SMP members |
| **RootRecord Realm** | Web hub + Paper plugin — account linking, stats, featured SMP | RootRecord SMP players |
| **rootrecord.info** | Marketing, legal, account portal shell | Prospects + signed-in users |

## Shared platform services

These products share **conceptual** building blocks:

- **RootRecord account** — sign-in against cloud APIs; JWT-style sessions in mobile clients.
- **Primary API** — `api.rootrecord.info` for app data, auth extensions, earn summaries, weather routes, business routes, etc.
- **Billing** — Stripe-backed flows surface through site + API (exact UX varies by app).

## How to choose what to read next

- Shipping Android **business** features → [business-manager.md](business-manager.md)
- Weather + alerts architecture at user level → [weather-manager.md](weather-manager.md)
- Wallet UX and Solana **mobile** scope → [token-manager.md](token-manager.md)
- Cross-app **account** mental model → [account-hub.md](account-hub.md)
- Goal planning + AI plans (Release 0) → [root-goals.md](root-goals.md)
- Minecraft notes + coords + maps → [rootmc.md](rootmc.md)
- SMP linking, McMMO, playtime, plugin deploy → [minecraft-realm.md](minecraft-realm.md)
- Public **website** behavior → [marketing-website.md](marketing-website.md)

## Versioning reality

Each repo moves at its own pace. **Do not assume** feature parity between Windows and Android for every module unless the product page explicitly says so.
