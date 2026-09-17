# Tutorial: developer onboarding

## Day 0 — Accounts & access

1. Get **GitHub** access to Mobile and Web repos.
2. Get **Cloudflare** access for Workers if you deploy.
3. Never ask for production secrets in chat—use **1Password** or org standard.

## Day 1 — Clone & build

1. Clone **Mobile-Development-2026** and run `pnpm install`.
2. Pick **one** app (e.g. Business) and `pnpm --filter … start` until the web shell loads.
3. Clone **Web-Development-2026** and `npm ci` inside `cloudflare/rootrecord-primary` (or follow README).
4. Copy **`.dev.vars.example`** → **`.dev.vars`**.

## Day 2 — Trace a request

1. In the mobile app, sign in against **dev** or **staging** if available; otherwise read code paths only.
2. Open `router.ts` and find the route your button hits.
3. Read the handler + D1 touch points.

## Day 3 — Ship a fix

1. Create a branch per repo rules (`weather-work` / `business-work` or `main` hotfix process).
2. Add **tests** or manual checklist note in PR.
3. Deploy Worker only if on-call—otherwise open PR for review.

## Day 4 — Solana (if applicable)

1. Clone **`RootRecord/solana-rootrecord-site`** separately.
2. Read `SOLANA-SITE-CANONICAL-REPO.md` in the web workspace to avoid wrong trees.

## Mindset

- **Prove** production changes with deploy logs—don’t claim URL updates without verification.

## Related reading

- [../06-development/workspace-layout.md](../06-development/workspace-layout.md)
