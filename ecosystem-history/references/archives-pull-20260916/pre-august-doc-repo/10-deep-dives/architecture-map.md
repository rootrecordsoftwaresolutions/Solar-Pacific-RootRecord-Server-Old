# Architecture map

High-level system relationships. For authoritative route lists, read **`router.ts`** in `rootrecord-primary`.

```mermaid
flowchart TB
  subgraph clients [Clients]
    BM[Business Manager Android]
    WM[Weather Manager]
    TM[Token Manager]
    AH[Account Hub]
    BN[Block Notes Android]
    WEB[rootrecord.info Pages]
    SOL[solana.rootrecord.info Next.js]
  end

  subgraph cf [Cloudflare]
    API[api.rootrecord.info — primary Worker]
    BNAPI[api.rootmc.net — rootmc-api]
    LIC[rootrecord-license Worker]
    PAGES[Pages — static site]
    D1[(D1 SQLite)]
  end

  subgraph mc [Minecraft]
    PAPER[Paper + RootMC plugin]
    MYSQL[(MySQL)]
  end

  subgraph external [External]
    STRIPE[Stripe]
    RPC[Solana RPC]
    PROV[Weather providers]
  end

  BM --> API
  WM --> API
  TM --> API
  AH --> API
  BN --> BNAPI
  PAPER --> BNAPI
  PAPER --> MYSQL
  WEB --> LIC
  WEB --> PAGES
  WEB --> BNAPI
  BNAPI --> D1
  API --> D1
  API --> STRIPE
  API --> PROV
  API --> RPC
  SOL --> API
```

**Legend:** Arrows are logical dependencies, not every HTTP call. Solana Tools may also call Vercel-hosted APIs directly for Next-specific routes, while Worker forwards some paths.

## Related reading

- [../03-platform/api-overview.md](../03-platform/api-overview.md)
