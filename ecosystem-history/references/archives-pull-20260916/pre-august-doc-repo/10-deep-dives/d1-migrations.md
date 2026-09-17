# D1 migrations primer

D1 uses SQL migrations checked into **`cloudflare/rootrecord-primary/migrations/`** (exact folder name may vary—verify).

## Rules of thumb

1. **Forward-only thinking** — production rollback is painful; test migrations on preview DBs first.
2. **Small steps** — prefer additive columns + backfill jobs over big-bang rewrites.
3. **Compatibility** — old app versions may still hit old columns during rollout—coordinate with `MIN_APP_VERSION_*` bumps.

## Teaching SQL reviewers

Ask: “Will this lock tables during peak?” SQLite has different locking characteristics than Postgres—read Cloudflare D1 docs when in doubt.

## Related reading

- [../03-platform/data-and-storage.md](../03-platform/data-and-storage.md)
