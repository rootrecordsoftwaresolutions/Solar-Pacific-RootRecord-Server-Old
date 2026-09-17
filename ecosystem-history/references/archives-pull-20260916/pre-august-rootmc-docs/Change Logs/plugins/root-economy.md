# Root-Economy

## [1.8.1] — 2026-08-03

- **Solar Gold mine mult fix** — poll `api.rootmc.net` **first** (Shockbyte). Do not treat `api-local` 502 HTML as live; fall through only when the preferred feed is actually `online`.
- Loan ore-sweep line shows active solar mult (`· solar 1.150x`).
- Formula unchanged: `multiplier = 1 + (bankSOC/100)` while Eco feed live; else `1.0×`.

**Deploy:** upload `root-economy-1.8.1.jar` to Claims + Towny (+ Test); delete `root-economy-1.8.0.jar`; restart. No yml required unless you customize `solar-mining:` / `ore-sweep`.
