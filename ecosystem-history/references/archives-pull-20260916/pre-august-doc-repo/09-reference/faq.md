# Frequently asked questions

## What is RootRecord?

A software company shipping **mobile-first productivity apps** (business + weather), **account services**, **billing**, and **Solana tooling**, unified under one brand.

## Is RootRecord open source?

Some artifacts may appear on GitHub (releases, installers). **Availability varies by product**—check official links on rootrecord.info.

## Where is the API?

**`https://api.rootrecord.info`** — Cloudflare Worker `rootrecord-primary`.

## Where is the Solana app?

**`https://solana.rootrecord.info`**, built from `solana-rootrecord-site/` in this monorepo and synced to GitHub `RootRecord/solana-rootrecord-site`.

## Do you custody my seed phrase?

**No** for Token Manager / standard wallet flows—those are **non-custodial**. Custodial features, if offered, are **separate** flows with explicit UX.

## Which folder do I use for mobile?

`Mobile/` (pnpm workspace) inside `MonoRepo/`.

## Why is this now one git root?

To keep pathing, docs, and automation consistent across mobile, web, and workers in a single workspace.

## How do I report a security issue?

Follow the organization’s security disclosure policy (add link when published). Do **not** open public issues with live exploits.

## Related reading

- [../01-introduction/what-is-rootrecord.md](../01-introduction/what-is-rootrecord.md)
