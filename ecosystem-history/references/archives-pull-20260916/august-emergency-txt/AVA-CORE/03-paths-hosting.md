# Paths, hosting, and failover

## Canonical homes

| Role | Path |
|------|------|
| Ava handoff (single truth) | `E:\.Ava_Ivy` · Linux pit-stop `/mnt/e/.Ava_Ivy` |
| RootMC workspace (prefer E) | `E:\.1 Work Stations\RootMC\` |
| Ava Node runtime | `Web Files\rootmc-ava\` → status `http://127.0.0.1:8787/` |
| Laptop kit (this pack) | `Emergency pack\Ava Laptop\` — **do not relocate EXE** |
| D twin (until cutover wipe) | `D:\.1 Work Stations\` — sync via robocopy; not primary edit target |
| Secrets | RootMC `.env` + `Emergency pack\.credentials\` |

### Env locks

| Env | Value |
|-----|--------|
| `AVA_HANDOFF` | `E:\.Ava_Ivy` |
| `AVA_WORKSPACE` | `E:\.1 Work Stations\RootMC` |

Handoff junctions historically pointed Server Handoffs Ava Ivy → `E:\.Ava_Ivy`. Prefer editing on **E**.

## Public URLs

| Service | URL |
|---------|-----|
| Game | play.rootmc.net |
| API | https://api.rootmc.net |
| Site | https://rootmc.net |
| Map | https://map.rootmc.net |

## Product / network truth

- Live games: **Claims** + **Towny** (Shockbyte) — Towny stays; upgrade with Paper 26.3, do not remove
- Gen2 retired — do not revive as production
- Player currency: Gold (G); API is api.rootmc.net (not RootRecord shards)
- Production jar cutovers: human FileZilla + Shockbyte restart until gated later phase

## Disk identity (wipe by serial, not letter)

| Disk | Serial | ~Size | Role |
|------|--------|-------|------|
| HGST | `JR100X4M1R3TUE` | 931 GB | Typical **D:** Work Station twin / flash target when operator says |
| LITEON | `TW059X3VLOH008AF04M1` | 119 GB | **C:** OS SSD |
| JMicron | `ZDZSCFL4` | 1863 GB | **E:** Portable Archive — RootMC source / Ubuntu `/mnt/e` — **do not format as system disk** |
| Generic STORAGE | `000000000819` | 119 GB | Removable / installer staging |

Never "wipe D/E by letter alone." Confirm serial in installer / `lsblk`.

## Single Ava tree

Only **one** host may run `rootmc-ava` posting as Ava. Two supervisors → duplicate replies and raced polls. Stop Windows Ava before Linux systemd; clear sticky `power-off.json` before Linux start.

## Hosting status (dump ~2026-08-06)

| Role | Host | Status at dump |
|------|------|----------------|
| Primary brain host | OptiPlex Ubuntu `ava-core` @ `192.168.1.62` | **UNREACHABLE** |
| Failover | Laptop Node poller `:8787` | Temporary recovery path |
| Dream / cloud keys | Removed after spend | Discord must not require dream path |
| Dig-health | Dream `http_403` outage recorded | Cursor/llama failover |

### Brain ladder (current)

1. Root Server digs (Cursor / on-device) when host alive
2. Local organizer (Ollama/llama) when OptiPlex reachable
3. Discord dream-state cloud fallback when host dark — **never name the vendor publicly**
4. Lockout = Alex verified DMs only (companion) — distinct from Mode 1 llama-only

### Migration

- Do **not** wipe Windows until `E:\MIGRATION-READY.txt` exists
- Status: `E:\windows backup\logs\migration-STATUS.txt`
- Prefer E for live edits; caches/builders on SSD / Ubuntu home

## Telegram `/deep` (Alex only)

| Command | What |
|---------|------|
| `/deep` | Delta dump since last watermark |
| `/deep full` | Broader backfill |
| `/deep status` | Last watermark + stats |
| `/deep help` | Help |

Evidence packs historically under `E:\.Ava_Ivy\reports\deep-dumps\` and pack `_forensics/`.
