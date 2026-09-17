# Staff briefing automation (2026-08-03)

Melee GM → Ava offered pulse/report (`1533898904272109719`). Melee asked full ranked report; Ava stalled ("not guessing" / dream-dark).

## Shipped

`src/staffBriefing.mjs` — instant, no Cursor:
- **GM** → offer quick pulse vs full report
- **quick pulse** → solar + live/staged crumbs
- **full report** → ranked **1–10** since yesterday

Wired in `pipeline.mjs` before hush/dig (same lane as `/solar`). Soft-chat fallback GM line in `classify.mjs`.

## Restart

Ava poller restart picks this up live.
