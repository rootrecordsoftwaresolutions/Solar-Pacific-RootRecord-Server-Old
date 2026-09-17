# Time off — helpful admin (Grok / dream when Root Server offline)

**Date:** 2026-08-02  
**Ask:** When Ava favors cloud brain because Cursor/Root Server digs are offline, call it **time off** — she is only a helpful server admin, but she still knows everyone.

## Trigger

Time off when **awake** and dream brain is required because:

- no `CURSOR_API_KEY` / SDK key, or
- `AVA_FORCE_DREAM=1`, or
- Cursor recommend failed → dream failover

**Not** time off: operator **sleep** (keeps “dreaming” frame), **hush**, **power-down**, **cloud-dark** silence.

## Behavior

- Persona / dream SYSTEM: “time off — helpful admin only”
- Heartbeat / status mode: `time-off` (ava status page label)
- **People packs always packed** (`gatherPeopleContext` in `dreamBrain.mjs`) — Alex, Melee, Zuppa, asker cues
- Dig / jar / implement asks → redirect (Slack / when Root Server is back)
- Alex wish=command still applies for admin-safe asks
- Discord autonomy when Cursor **is** online unchanged

## Runtime files

- `src/surfaceRules.mjs` — `isTimeOffAdminMode`, dig redirect copy
- `src/dreamBrain.mjs` — `timeOff` mode line + dig steer
- `src/recommend.mjs` — pass `timeOff`, hard dig redirect
- `src/persona.mjs` — locked cue
- `src/poller.mjs` / `src/statusPage.mjs` — mode `time-off`
- `dream-pack/SYSTEM.md` — time-off admin lore
