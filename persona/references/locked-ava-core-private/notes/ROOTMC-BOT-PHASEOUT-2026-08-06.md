# RootMC Official bot → Ava phase-out

**Date:** 2026-08-06  
**Status:** `/server` on Ava · Official command list stripped · full kick still gated

## Done this pass
- [x] Ava slash + text `/server` (`core/src/serverCommand.mjs`)
- [x] Register on Ava app at boot (`poller.mjs`)
- [x] Pipeline text `/server`
- [x] Official register script: `/server` removed from commands array
- [x] Official handler stub redirects to Ava (source; needs Worker deploy to go live)
- [ ] Re-run Official `discord-register-rootmc-commands.mjs` against Discord API
- [ ] Deploy `rootmc-realm-api` Worker so interactions stub is live
- [ ] Soak / announce

## Inventory — what still needs moving

| Command / surface | Official | Ava | Priority |
|-------------------|----------|-----|----------|
| `/server` | strip / stub | **live** | P0 done |
| `/solar` `/status` (power) | — | live | keep |
| `/help` | Official | missing | **P1** |
| `/link` | Official | missing (bind API exists) | **P1** |
| Proposal vote **buttons** | Official | text votes SoT | **P1** retire (double-count risk) |
| `/balance` `/bal` `/pay` | Official | missing | P2 policy |
| `/value` `/worth` | Official | missing | P2 policy |
| `/vote` slash % | Official | text ballots #voting | P2 |
| `/proposal` `/proposals` | Official Discord | in-game drain | P2 |
| `/systemreport` `/devuptime` | Official staff | missing | P2 keep/drop |
| Timezone role select | Official | missing | P2 |
| Daily/weekly reports, awards, dividend, boards | Worker crons | soft-ack | **keep Worker** (not a Discord slash migrate) |
| Paper `rootmc-official` jar | Paper | n/a | separate |

## Apps
- Official: `1511794429986345020` · `api.rootmc.net` interactions
- Ava: `1532751879875072070` · gateway · `ava-ivy.service`

## Kick Official only after
1. P1 `/help` + `/link` (or explicit drop)
2. Economy slash policy
3. Proposal buttons ignored
4. Alex sign-off (`rootmc-bot-parity.md`)

## Naming
Ava `/status` = solar/power. Minecraft status = `/server`.
