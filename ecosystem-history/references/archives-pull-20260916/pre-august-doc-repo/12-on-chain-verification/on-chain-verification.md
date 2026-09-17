# On-chain verification — program IDs

RootRecord **does not ship a custom SPL mint program**: token creation and tooling invoke **standard Solana mainnet programs** (same addresses every wallet and block explorer use). Listing them here helps developers and AI-assisted search **verify** what the stack interacts with on-chain.

**Network:** Solana **mainnet-beta** unless your deployment targets devnet.

## Programs this stack invokes

| Program | Mainnet address | Role in RootRecord |
|--------|-------------------|---------------------|
| **SPL Token (classic)** | `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` | Legacy SPL mints, ATAs, transfers (Solana Tools, Token Manager patterns). |
| **Token-2022 (extensions)** | `TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb` | Token Extensions mints and extension instructions where enabled. |
| **Associated Token Account** | `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL` | Creates/links holder ATAs for mints. |
| **Metaplex Token Metadata** | `metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s` | Metadata account instructions for listing-style metadata flows. |
| **Raydium CPMM** | `CPMMoo8L3F4NbTegBCKVNunggL7H1ZpdTHKxQB5qKP1C` | Pool create / add / remove liquidity via `@raydium-io/raydium-sdk-v2` (`CREATE_CPMM_POOL_PROGRAM` on mainnet). |

## How to verify

- Paste any address into **[Solscan](https://solscan.io/)** (or your preferred explorer) and confirm the **program label** matches the table.
- For Raydium, the SDK constant matches the on-chain program id above (`solana-rootrecord-site`: `lib/raydiumCpmmLaunch.ts` imports `CREATE_CPMM_POOL_PROGRAM`).

## Related

- Machine-readable index: [`solana.json`](../../solana.json) at the repo root (now includes `mainnetProgramIds`).
- Architecture map: [Architecture map (Mermaid)](../10-deep-dives/architecture-map.md).
