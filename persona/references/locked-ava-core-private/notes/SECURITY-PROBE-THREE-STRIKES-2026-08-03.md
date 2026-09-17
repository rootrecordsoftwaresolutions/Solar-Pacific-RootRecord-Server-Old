# Security probe gate — 2026-08-03

If someone asks for **secrets** or **too-detailed security info** (tokens, `.env`, passwords, panel logins, private keys, DB connection strings, etc.):

1. **Warn** (1/3) — hard no
2. **Warn** (2/3) — firmer
3. **Warn** (3/3) — final
4. On the **next** such ask after 3 warnings: **trust → 0**, note `security-distrusted`, digs locked, and a **cry for help in #admins** tagging **who / what / where**

Alex is exempt. Clear with Alex by removing `security-distrusted` and restoring trust on their player profile under `data/players/`.

Code: `live/rootmc-ava/src/securityProbe.mjs` (wired in `pipeline.mjs` + `recommend.mjs`).
