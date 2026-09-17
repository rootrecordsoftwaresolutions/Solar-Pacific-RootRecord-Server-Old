# Web: Workers & Pages deploy

## Workers (npm)

Inside each Worker package:

```bash
npm ci
```

Copy **`.dev.vars.example`** → **`.dev.vars`** for local dev. Production secrets use **`wrangler secret put`**.

### Primary Worker

Path: `cloudflare/rootrecord-primary`

Deploy via that package’s `deploy.ps1` or documented `wrangler deploy`, with **`credentials.env`** available upward in the tree for deploy scripts (gitignored).

### D1 migrations

Apply migrations using scripts in `package.json`—**read the Worker README** before running against production.

## Pages (rootrecord.info)

Path: `Web/main`

Deploy:

```bash
npx wrangler pages deploy .
```

Verify **`/api/site-config`** returns the intended `ROOTRECORD_API_BASE`.

## Solana Tools site

Use `solana-rootrecord-site/` at the monorepo root.

## Related reading

- [../03-platform/cloudflare-workers.md](../03-platform/cloudflare-workers.md)
