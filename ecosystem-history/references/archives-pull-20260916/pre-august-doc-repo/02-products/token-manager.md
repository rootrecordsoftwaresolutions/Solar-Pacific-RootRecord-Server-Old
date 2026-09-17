# Token Manager (mobile)

**RootRecord Token Manager** is the **Android-first** mobile expression of Solana wallet workflows that mirror the public **Solana Tools** web app’s *core* wallet paradigms—without turning the phone app into a fork of every browser-only page.

## Principles (from product intent)

1. **Non-custodial** — The app must not solicit or store seed phrases.
2. **External wallets** — Connect via standard wallet adapters / Phantom-style flows appropriate to mobile WebView or deep links.
3. **Libraries** — `@solana/web3.js` and SPL token libraries on the client for reads and transaction building; RPC calls happen from the device with user consent.

## Backend role

The mobile project may include a **small FastAPI** service for preferences, address book, cached SOL price, and similar **non-key** data. That service is **not** the source of truth for financial custody.

## Relationship to solana.rootrecord.info

The **canonical public site** lives only in **`RootRecord/solana-rootrecord-site`** (deployed via `git push` from that repo). Workspace copies under other paths are **not** alternate publishing lanes—see workspace rules.

## Teaching users

- **Watch mode** vs **connected wallet** — explain what each can and cannot do.
- **Network selector** — mainnet vs devnet vs testnet affects balances and risk.
- **Fees & failures** — Solana RPC errors and slippage are user-visible realities.

## Related reading

- [../05-solana/solana-ecosystem.md](../05-solana/solana-ecosystem.md)
- [../05-solana/solana-tools-site.md](../05-solana/solana-tools-site.md)
