# PROP-01 — production restart (closed)

**Status:** math shipped; production on **root-skills 1.8.0** (verified 2026-08-06). **No auto-restart** policy unchanged for future gates.

## Staged jars (examples)

- `workstations/rootmc/Server Handoffs/Upload Staging/singular-full-latest/plugins/root-skills-1.8.1.jar`
- `workstations/minecraft-test/plugins/root-skills-1.8.1.jar`
- Plan: `plans/PROP-01.md`

## Who restarts

Shockbyte / production Paper operator (Alex). Ava stages only — never restarts live game host from dig automation.

## After restart

1. Confirm Root-Skills version in `/plugins` matches staged 1.8.1+ with PROP-01 curve.
2. Spot-check XP climb on a mid-level skill (not flat early / cliff late).
3. Note in `#updates` + mark PROP-01 complete in plans.
