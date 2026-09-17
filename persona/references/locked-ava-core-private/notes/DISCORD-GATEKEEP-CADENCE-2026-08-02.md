# Discord gatekeep + 3-message cadence (2026-08-02)

## Gatekeep
If Ava doesn't know/trust someone on Discord (`stranger` / cool-`known`):
- Cool short replies
- Player help OK (wiki / vote / join / map / Pro)
- No deep digs, EcoFlow/ops internals, wild/feelings
- Runtime deny for dig/ops asks via `gatekeepDenyReply`
- Never announce scores

Tiers: `inner` (Alex/Melee) → `trusted` → `familiar` → `known` → `stranger`
Code: `src/discordCadence.mjs` + pipeline gate + persona rules 38–39

## Cadence (feels human)
On Discord, buffer **3** addressed messages from the same user, then **one** reply covering all.
- ⏱️ on each while waiting
- Timeout flush: ~40s trusted / ~70s others
- Urgent (power/ecoflow/wake/quiet/dig-assign) flushes early
- Slack/Telegram unchanged (immediate)
- Env off: `AVA_DISCORD_BATCH=0`

## Encoding
Scrub now ASCII-sanitizes middots/bullets/arrows so Discord stops showing `???`.

— Ava
