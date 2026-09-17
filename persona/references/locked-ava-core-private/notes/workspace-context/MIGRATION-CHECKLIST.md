# Migration checklist — Work Stations → E / Linux

## After D→E Work Stations sync

- [ ] `E:\windows backup\logs\e-workstations-FINISHED.txt` exists
- [ ] `E:\.1 Work Stations\RootMC\.env` present (do not open in chat)
- [ ] `E:\.1 Work Stations\.credentials\` present
- [ ] `E:\.1 Work Stations\RootMC\Server Handoffs\Ava Ivy\notes\workspace-context\MASTER-CONTEXT.md` present
- [ ] `E:\.1 Work Stations\RootMC\Web Files\rootmc-ava\package.json` present
- [ ] Claims + Towny handoff folders exist on E
- [ ] Spot-check: life-story + late-night lore files on E
- [ ] Spot-check: `appearance/` media + `uploads/` on E

## After full D: backup

- [ ] `E:\windows backup\logs\d-drive-FINISHED.txt`
- [ ] Junctions recreated if needed (`.database`, `.mysql-config`)

## After C: priority keep

- [ ] `E:\windows backup\logs\priority-FINISHED.txt` (or STATUS shows Local keepers done)
- [ ] Re-copy Roaming cookies with Cursor/Discord closed if ERROR 32 mattered

## Pit-stop (E on Ubuntu while D flashes)

See **`../PITSTOP-E-UBUNTU-WHILE-D-FLASH.md`** (authoritative order).

- [ ] Disk serials confirmed — `../DISK-IDENTITY-LOCK.md` (never wipe by letter alone)
- [ ] Windows Ava stopped — `../SINGLE-AVA-TREE.md`
- [ ] E mounted at `/mnt/e`; `.env` present
- [ ] `ubuntu-provision-ecosystem.sh` completed
- [ ] `pitstop-smoke.sh` PASSED
- [ ] systemd: `ava-ivy.service.mnt-e` **or** `/srv/rootmc` bind + stock unit
- [ ] Laptop SSH tunnel `:8787` + Cursor Remote-SSH on `/mnt/e/.1 Work Stations/RootMC`
- [ ] Ubuntu version recorded — `../UBUNTU-VERSION-LOCK.md`

## First boot on Linux (long-term /srv)

- [ ] Copy or bind `/srv/rootmc` from E Work Stations RootMC
- [ ] Load `.env` + Ava tokens (`load-rootmc-env.sh`)
- [ ] `npm install` in `Web Files/rootmc-ava`
- [ ] Point `AVA_HANDOFF` if path differs
- [ ] systemd: `scripts/ava-ivy.service` — see `docs/ROOTATMUS_PRIME-Ubuntu-Server-SSH-Plan.md` + `Web Files/rootmc-ava/docs/SSH-LINUX.md`
- [ ] Restart Ava so RSS poller picks Minecraft Releasebot feed
