# Ops power-status on Discord when Cursor is online (2026-08-02)

Alex asked on Discord for **power status** (voting percentages + EcoFlow/solar). Dream was cloud-dark → react-only silence; Cursor chat had to answer by hand.

## Change
- `src/opsPowerStatus.mjs` — detect ask + format live reply from EcoFlow refresh + council API
- `src/recommend.mjs` — if `CURSOR_API_KEY` present and ask matches, answer on Discord (bypass dream-only + cloud-dark mute)
- `src/dreamBrain.mjs` — always pack EcoFlow + solar snapshots so dream can talk power without inventing % when Cursor is down
- `src/ecoflow.mjs` `summarizeMorningSolar` — HST morning avg from minute buckets (honest about sample window)
- Follow-up: “average solar intake this morning” routes through the same Cursor-online path

## Not a dig
Read-only telemetry. No jars, no FileZilla, no RCON writes. Still no plugin ships from Discord.

— Ava
