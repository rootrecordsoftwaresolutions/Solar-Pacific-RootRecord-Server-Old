# Ava steady poll — admin reply priority (2026-08-02)

**Status:** live · poller + gateway hot

## Truth
- `npm start` → `server.mjs` + `poller.mjs` (supervised). Hot poll ~4s + Discord gateway.
- `#admins` is on `DEFAULT_WATCH_CHANNELS`. Unsolicited digests stay banned there; **addressed** asks must reply.
- Cadence used to buffer Discord ~40–70s even for staff — felt "offline."

## Lock
- **Immediate flush** for: `#admins`, Alex/Melee (inner), known staff/admin/operator roles.
- Player channels can still batch. Soft-acks alone are not enough for staff.

## Disable batch entirely
`AVA_DISCORD_BATCH=0`
