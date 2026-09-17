# What is RootRecord?

**RootRecord** is a software brand and platform that ships **multi-device applications** together with **web accounts**, **billing**, and (where relevant) **Solana-related tooling**. The name suggests something *grounded*: local-first or device-native experiences, optional cloud sync, and a coherent account layer across products.

## In one paragraph

RootRecord builds productivity and utility software—notably **Business Manager** (operations, time, money, clients) and **Weather Manager** (hazard-aware weather and alerts)—plus **mobile Solana wallet-adjacent tools** (**Token Manager**), **Block Notes** (Minecraft companion), **Root Goals**, and a central **Account Hub** for profile, subscription, and cross-app entry points. The public **rootrecord.info** site explains the lineup, hosts legal pages, and links to account flows that talk to Cloudflare-based APIs.

## What RootRecord is *not*

- It is not a single monolithic “super-app” that does everything in one binary. Different apps have different installables and codebases.
- It is not “only crypto.” Solana features are one surface area; the core business is cross-platform client software and cloud accounts.
- It is not a guarantee of any financial return. Where token or earn features exist, they have their own rules and risks—read product copy and on-chain reality, not this doc, for investment-like claims.

## The three layers (mental model)

1. **Clients** — Android (and in some cases Windows) apps, built with modern web tech (e.g. React + Capacitor) where noted in each repo.
2. **Cloud API** — `https://api.rootrecord.info` is the primary production API (Cloudflare Worker: `rootrecord-primary`). It handles auth, entitlements, weather and business data routes, earn summaries, and more.
3. **Web presence** — Static **Cloudflare Pages** for **rootrecord.info**; the **Solana Tools** site is a separate public repo deployed to its own host (e.g. Vercel) at **solana.rootrecord.info**.

If you understand those three layers, you can place almost any feature or bug in the right box.

## Why “RootRecord” matters as a story

Users rarely care about your Worker names. They care whether:

- the app **works offline or degraded** when expected,
- their **account** works across products,
- **billing** is understandable,
- **privacy** promises match behavior.

This documentation uses “RootRecord” to mean that **product + account + API + web** bundle—not any single repository.

## Next steps

- Product lineup: [../02-products/overview.md](../02-products/overview.md)
- Technical map: [../03-platform/api-overview.md](../03-platform/api-overview.md)
- Terms you will see everywhere: [glossary.md](glossary.md)
