# Staging — rootmc + root-play 1.8.0 (502 fallback) — 2026-08-03

Built with **rootrecord-common** patch: HTTP 502 / `error code: 502` → fall back to `api.rootmc.net`.

Verified inside `rootmc-1.8.0.jar`: `RootMcApiBases` contains `http 502`, `error code: 502`, `upstream unavailable`.

## Jars

| File | Size |
|------|------|
| `rootmc-1.8.0.jar` | ~5.2 MB |
| `root-play-1.8.0.jar` | ~4.8 MB |
| `rootmc-official-1.8.0.jar` | restored after accidental prune (~4.7 MB) |

## Staged (E:)

- `Server Handoffs/1. RootMC - Claims/plugins`
- `Server Handoffs/2. RootMC - Towny/plugins`
- `Server Handoffs/3. RootMC - Test/plugins`
- `Plugin Building/Minecraft/server/host-handoff/plugins`
- `Web Files/rootmc-web/public/plugins`

`local.properties` live/handoff paths pointed at **E:** Towny for this build.

## You

1. FileZilla upload **Claims + Towny** (`rootmc-1.8.0.jar`, `root-play-1.8.0.jar`)
2. Shockbyte restart when quiet
3. Watch `#server-logs` — vote-mirror / gold transfer / dividend / sync should retry prod instead of sticking on api-local 502

See: `HTTP-502-API-LOCAL-FALLBACK-2026-08-04.md`
