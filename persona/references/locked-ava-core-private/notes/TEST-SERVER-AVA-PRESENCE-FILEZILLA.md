# Test Server — FileZilla steps (Ava presence Phase 1)

**Host:** Test Server only (`Server Handoffs/3. RootMC - Test Server/`)  
**Do not** restart Claims/Towny for this.

## Upload
1. `plugins/root-ava-core-1.8.4.jar` (remove older `root-ava-core-*.jar` first)
2. `plugins/RootMC/root-ava-core.yml` (must have `presence.enabled: true`)

## Restart
Operator restarts **Test Server** when ready.

## Smoke
- `/ava presence` → enabled + spawned
- Stand near spawn; body should idle-wander within ~6 blocks
- `/ava presence here` (op) re-anchors at your feet
- Confirm display name **Ava Ivy**; no auto-chat (summon-only / no brain yet)

## Rollback
Set `presence.enabled: false` + `/ava reload` (or despawn) · or remove jar and restore 1.8.3.
