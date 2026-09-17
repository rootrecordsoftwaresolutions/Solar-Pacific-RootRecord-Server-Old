# Ava surface architecture (locked)

**Discord** = dream state (communal). Cloud brain + **D1 / api.rootmc.net**. No Root Server digs / jar ships.

**Exception (Cursor online):** ops **power status** asks (EcoFlow / solar SOC + council voting shares) may answer from live Root Server packs on Discord — read-only telemetry, not a plugin dig. Bypasses cloud-dark mute. See `src/opsPowerStatus.mjs`.

**Slack** = all development. On-device **Root Server** (Cursor + filesystem).

**Web** (rootmc.net / wiki) = communal organization + knowledge pages.

**Operator sleep** = soft offline until ~10:00 HST (still dream brain on Discord; digs paused).

## Code
- `Web Files/rootmc-ava/src/surfaceRules.mjs`
- `Web Files/rootmc-ava/src/dreamBrain.mjs` — Discord replies (+ eco/solar packs)
- `Web Files/rootmc-ava/src/opsPowerStatus.mjs` — Cursor-online power/voting status
- `Web Files/rootmc-ava/src/cursorBrain.mjs` — Slack / Root Server digs
- `Web Files/rootmc-ava/src/recommend.mjs` — routes by `surface`

## Test
Restart Ava, ping Discord → dream replies. Dig on Slack → Root Server.
Ask “power status / voting % / ecoflow solar” with `CURSOR_API_KEY` set → live pack reply on Discord.
