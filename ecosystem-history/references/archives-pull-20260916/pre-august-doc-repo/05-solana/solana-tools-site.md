# Solana Tools public site

The browser experience at **`https://solana.rootrecord.info`** is implemented **only** in the GitHub repository:

**`https://github.com/RootRecord/solana-rootrecord-site`**

## Canonical workflow

1. Clone that repo **once** per machine (or use a single worktree).
2. Develop at the repo root with **pnpm**.
3. Deploy with **`git push origin main`** (Vercel or configured CI).

## Forbidden patterns (workspace rules)

Routine development must **not**:

- Edit shadow copies under `Web/solana/solana-rootrecord-site` as if they were shipping.
- Deploy via robocopy “publish” pipelines or extra remotes named `solana-site`.
- Claim production updated without a successful push to the canonical repo.

Read **`Web/solana/SOLANA-SITE-CANONICAL-REPO.md`** in the web workspace for the authoritative note.

## API forwarding

The primary Worker can forward selected **`/api/ecosystem/*`** and **`/api/solana-site/*`** paths to the Vercel origin using `SOLANA_TOOLS_API_FORWARD_URL`, while still handling native routes like logging or Discord notify locally.

## Teaching users

Point them to **solana.rootrecord.info** for the **official** UI. Random forks may be outdated or malicious.

## Related reading

- [solana-ecosystem.md](solana-ecosystem.md)
- [../03-platform/api-overview.md](../03-platform/api-overview.md)
