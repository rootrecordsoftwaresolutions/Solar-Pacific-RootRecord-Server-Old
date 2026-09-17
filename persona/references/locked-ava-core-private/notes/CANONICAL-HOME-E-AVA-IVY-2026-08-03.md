# Canonical Ava home — 2026-08-03

- **Home:** `E:\.Ava_Ivy` (Linux: `/mnt/e/.Ava_Ivy`)
- **Cause of post-transfer confusion:** `AVA_HANDOFF` still pointed at `D:\...Server Handoffs\Ava Ivy` while notes/`data` on E had diverged.
- **Fix:** Moved E handoff → `E:\.Ava_Ivy`, merged newer D state, junctioned both E+D handoff paths to the home, set `AVA_HANDOFF` + `AVA_WORKSPACE` in RootMC `.env`, updated `config.mjs` / laptop / start-ava.sh defaults.
- **Rebuild packs:** `context/emergency-pack`, `context/avas-core`
- **Live code:** `live/rootmc-ava` (+ desktop, laptop, plugin, RootMC workspace)
