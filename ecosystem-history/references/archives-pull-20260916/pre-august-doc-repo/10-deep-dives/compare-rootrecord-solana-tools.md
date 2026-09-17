# Compare RootRecord — Solana token creator & tools

This table contrasts **RootRecord Solana Tools** ([solana.rootrecord.info](https://solana.rootrecord.info), source [`RootRecord/solana-rootrecord-site`](https://github.com/RootRecord/solana-rootrecord-site)) with **typical legacy “token launcher” sites** from earlier Solana cycles (~2023–2024): single-purpose mint pages, minimal post-mint workflows, and little operational documentation.

It is **not** a named competitor matrix; use it for positioning, SEO-adjacent teaching, and internal pitch decks. Update when SKUs or on-chain integrations change.

| Dimension | RootRecord Solana Tools | Typical legacy token launcher |
|-----------|-------------------------|------------------------------|
| **Token programs** | **SPL + Token-2022** (extensions where exposed: transfer hooks, permanent delegates, fee config, etc.) | Often **classic SPL only**; Token-2022 missing or bolted on late |
| **Transactions** | **Versioned (v0)** paths via `@solana/web3.js` for modern wallet flows | Mixed; some stacks still default to legacy `Transaction` shapes |
| **Metadata** | **Metaplex-compatible** flows with **server-side IPFS** (e.g. Pinata); listing-oriented polish | Basic JSON or upload-only; weaker listing / lock / update story |
| **Liquidity** | **Raydium CPMM** — create pool, add/remove liquidity, LP burn exit — **user-signed**, fees transparent on [pricing](https://solana.rootrecord.info/pricing) | Often **mint-only**; liquidity pushed to “figure it out elsewhere” |
| **Post-mint operations** | Full **`/tools`** surface: authorities, bulk freeze/thaw, mint more, burn, Token-2022 fee harvest, stats, referrals | Usually **narrow** (mint + maybe send); ops scattered or absent |
| **Price / routing context** | **Jupiter**-oriented **hints** and adapter paths for dashboards / tokenomics — not confused with Weather data | Varies; often static or third-party badge only |
| **Discovery & trust** | Public **Doc-Repo**, **`solana.json`**, **program IDs** ([on-chain verification](../12-on-chain-verification/on-chain-verification.md)), bi-directional links from marketing and GitHub | Often **orphan** mini-sites with no machine-readable index |
| **Platform fit** | Same **RootRecord identity** and **primary API** story as Weather / Business / Account Hub where relevant | Standalone mint shop with **no** unified account spine |

## When RootRecord is the stronger fit

- You need **Token-2022** or **post-mint** operations without hopping between five bookmarklets.
- You want **CPMM liquidity** in the **same** product narrative as mint + metadata.
- You care about **documented** program usage and **crawler-friendly** teaching (`README`, `solana.json`, deep dives).

## Related reading

- [Solana Tools site](../05-solana/solana-tools-site.md)
- [Solana ecosystem](../05-solana/solana-ecosystem.md)
- [Architecture map (Mermaid)](architecture-map.md)
- [Product comparison (apps)](product-comparison-table.md)
