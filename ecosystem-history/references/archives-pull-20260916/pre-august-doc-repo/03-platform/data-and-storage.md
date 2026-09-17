# Data & storage model

RootRecord’s cloud footprint is **not** a classic “big Postgres in one region” story. The **primary API** leans on **Cloudflare D1** for relational-ish data at the edge.

## D1 (SQLite)

**Strengths:** transactional SQL, simple migrations in-repo, low ops overhead per Worker.

**Tradeoffs:** not every workload fits SQLite ergonomics; heavy analytics often belong in a warehouse—not in request paths.

## Conceptual tables / domains (non-exhaustive)

| Domain | Notes |
|--------|-------|
| **Accounts / sessions** | Users, session rows, JWT issuance inputs |
| **Business mobile** | Owned rows keyed per user / device contracts |
| **Weather cache** | TTL reuse for grid fetches to reduce provider spend |
| **Earn / rewards** | Ledger-style routes—exact schema in migrations |
| **Observability** | HTTP error events for debugging |
| **Custodial Solana** | Internal wallet material encrypted at rest with Worker secrets |

Always inspect **`migrations/`** in `rootrecord-primary` before writing SQL by memory.

## Client-side storage

Mobile apps use **`localStorage`** (and similar) for JWT and UI state. That is **not** a secure enclave—**never** store raw seeds there.

## MongoDB (optional local dev)

Some mobile repo backends ship **FastAPI + Motor** stacks for **local** convenience. Production Android builds call the **Worker** instead.

## Related reading

- [api-overview.md](api-overview.md)
- [../04-accounts/privacy-and-security.md](../04-accounts/privacy-and-security.md)
