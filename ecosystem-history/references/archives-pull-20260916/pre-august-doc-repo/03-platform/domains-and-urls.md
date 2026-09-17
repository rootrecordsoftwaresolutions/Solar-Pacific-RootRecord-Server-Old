# Domains & URLs

Canonical endpoints you can rely on when explaining RootRecord to users or wiring integrations.

| Surface | URL | Notes |
|---------|-----|--------|
| **Marketing site** | `https://rootrecord.info` | Cloudflare Pages (`Web/main/`) — no Minecraft content |
| **RootMC site** | `https://rootmc.net` | SMP hub, verify, shops, market, wiki, plugins (`Web/apps/rootmc-web/`) |
| **RootMC API** | `https://api.rootmc.net` | Worker `rootmc-api` (`/api/rootmc/*`, `/api/realm/minecraft/*`) |
| **RootMC verify** | `https://rootmc.net/verify/` | Link Minecraft account after `/rootmc link` |
| **RootMC plugin jars** | `https://rootmc.net/plugins/` | Published Paper plugin downloads + `manifest.json` |
| **Primary API** | `https://api.rootrecord.info` | Worker `rootrecord-primary` (legacy; new features use product shards) |
| **Solana Tools (public)** | `https://solana.rootrecord.info` | Next.js app from **`RootRecord/solana-rootrecord-site`** only |

## Deep links & app schemes

Mobile apps may register **custom URL schemes** (e.g. `weathermanager://`, `businessmanager://`) for Account Hub → app handoff, with store fallbacks.

## Site config pattern

Marketing Pages expose **`/api/site-config`** so the front-end learns **`ROOTRECORD_API_BASE`** (and related flags) without hardcoding secrets.

## What not to do

- Do not teach users to trust random mirrors or **unofficial** GitHub forks for downloads—point to **official** releases and stores linked from rootrecord.info.

## Related reading

- [marketing-website.md](../02-products/marketing-website.md)
- [solana-tools-site.md](../05-solana/solana-tools-site.md)
