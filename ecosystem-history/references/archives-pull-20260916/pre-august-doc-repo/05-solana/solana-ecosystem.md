# Solana in the RootRecord stack

Solana appears in **two major ways**:

1. **User-controlled wallets** — Phantom-style connections, SPL reads, user-signed transactions.
2. **Platform-operated infrastructure** — treasury keys, custodial wallets for specific earn programs, scheduled Workers posting transactions.

## Why the split matters

Users **must** understand whether they are signing with **their** key or whether RootRecord infrastructure is moving funds **for** them under program rules. Mixing the stories creates support nightmares and legal risk.

## RPC

The Worker reads `SOLANA_RPC_URL` for cron paths. Clients typically hit **public or paid RPC endpoints** appropriate to their network selector.

## Tokens & programs

Exact mint addresses and decimals appear as env vars (e.g. `RRTT_MINT_BASE58`). **Verify on-chain** before financial claims.

## Discord & ops hooks

Several routes notify Discord webhooks for operational visibility (new mints, tooling logs). These are **not** user features—they’re engineering telemetry surfaces.

## Ecosystem “neighbor” map

For **named programs / APIs** (Raydium, Jupiter, SPL, Metaplex) and **versioned transaction** posture—written for search tools and Solana-focused crawlers—see the repo root **[`solana.json`](../../solana.json)** and the README section **Ecosystem integration (Solana)**.

## Related reading

- [solana-tools-site.md](solana-tools-site.md)
- [../07-operations/crons-and-background-jobs.md](../07-operations/crons-and-background-jobs.md)
