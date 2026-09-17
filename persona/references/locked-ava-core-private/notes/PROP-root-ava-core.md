# PROP — Root-Ava-Core plugin (governance)

**Poll window:** 7 days weighted vote in Discord `#voting`  
**Spend / ship:** **no live jar** until Alex greenlights **after** this proposal lands and (if opened) the vote passes.  
**Filed:** 2026-08-02

## Summary

Authorize design + eventual Paper plugin **Root-Ava-Core** as Ava’s in-game companion core (status hooks, safe command surface, host health signals) — **not** a replacement for Root-Core economy/claims.

## Why

- Ava currently lives on Discord/Slack/Telegram + Worker APIs.
- A thin dedicated plugin would give her first-class, auditable in-game hooks without stuffing more into Official or Root-Core.
- Keeps Claims vs Towny isolation; no cross-server economy bleed.

## Scope (if approved)

1. Read-only / soft signals first (online players, TPS, restart notices).
2. Explicit operator-gated actions only (never silent mass bans / economy rate edits).
3. Config + secrets stay per-host `cloud.yml` style — no credential sharing across Claims/Towny.
4. Versioning follows YEAR.MONTH.BUILD with other plugins.

## Out of scope

- Replacing RootMC Official Discord slash surface
- Auto Shockbyte restarts
- Minting Gold / treasury debit without existing API rules

## Vote question

**Approve Root-Ava-Core as a planned plugin** (docs + design now; jar only after separate operator greenlight)?

- ✅ For
- ❌ Against
- ➖ Abstain

## Operator note

Default for 2026-08-02 ops day: **docs + this PROP only**. Alex answers yes/no on jar after proposal visibility.

## Staging note (2026-08-02)

**Jar v1.8.0 staged** (local handoffs only — FileZilla upload + Shockbyte restart still operator):

- `Server Handoffs/1. RootMC - Claims/plugins/root-ava-core-1.8.0.jar`
- `Server Handoffs/2. RootMC - Towny/plugins/root-ava-core-1.8.0.jar`

Source: `Plugin Building/Minecraft/plugins/root-ava-core/` (package `com.rootrecord.minecraft.rootavacore`). Soft/read-only `/ava` status + `/ava reload` only.
