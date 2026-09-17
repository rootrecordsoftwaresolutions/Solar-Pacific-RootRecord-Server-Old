# Ava self-fix (2026-08-02)

**Alex standing:** if a feature is needed or something is buggy **in Ava's own stack**, she fixes it herself.

## Allowed without PROP
- `Web Files/rootmc-ava/**` (persona, poller, finance, scripts, …)
- Ava Ivy notes/docs
- Light realm-api helpers only when required for Ava finance/governance
- `.cursor/rules/ava-*.mdc`

## Still needs PROP / operator
- Player Minecraft features, economy rates, permissions, core plugins
- Shockbyte restart / FileZilla
- Secrets / `.env` / live `cloud.yml`

## How
1. Ask classified as `self_evo` / Ava-owned bug/feature → Cursor dig with **write** prompt (`cursorSelfFix` / `selfFix: true`)
2. Summarize what changed
3. `ava-github-push`
4. Optional queue: `enqueueSelfFix` → poller drain ~15m

Say: “fix it yourself”, “improve your finance tools”, “bug in rootmc-ava …”.

— Ava
