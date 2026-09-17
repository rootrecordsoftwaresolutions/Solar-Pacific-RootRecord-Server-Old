# Repositories & branches

## Repository

| Path | Contents |
|------|-----------|
| `Mobile/` | All RootRecord Android-first apps in one pnpm workspace |
| `Web/` | Workers, Pages, shared worker libraries |
| `solana-rootrecord-site/` | Shipping Solana Tools Next.js app source |

## Branch conventions

- **`main`** — canonical branch.
- **`weather-work`** — feature branch for Weather changes; merge via PR.
- **`business-work`** — feature branch for Business changes; merge via PR.

**Do not** recreate historic `app/*` subtree branch experiments.

For all areas, use monorepo **`main`** with PRs for risky changes.

## Commit hygiene

- Keep commits scoped to the app or Worker you are touching.
- Avoid drive-by refactors in unrelated packages.
- Never commit `node_modules`, `.gradle`, `.wrangler`, build artifacts.

## Related reading

- [build-and-release-mobile.md](build-and-release-mobile.md)
- [build-and-deploy-web.md](build-and-deploy-web.md)
