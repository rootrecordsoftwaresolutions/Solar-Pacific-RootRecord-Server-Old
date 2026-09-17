# Ava / RootMC — Master Plan (tackle everything)

**Written:** 2026-08-03 (HST) / 2026-08-04 UTC  
**Sources:** operator Telegram DM (`tg:6644482344`), Discord `#random-facts` msg `1534036095581425694`, urgent registry, pit-stop kit, jobs, today’s Control Panel / rewrite work, Fern Forest context  
**Canonical home:** `E:\.Ava_Ivy`  
**Host public name:** HI Pacific Solar Root Server  

---

## 0. Directives from you (locked in)

### Telegram DM (WildEcho94 → Ava) — 2026-08-04 ~02:59 UTC

| # | You said | What it means |
|---|----------|----------------|
| 1 | “Hi Ava” | Operator check-in |
| 2 | “Trying to figure out all the control panel stuff you just built me, the .exe connection” | **Explain + harden** Control Panel ↔ `Ava Ivy.exe` ↔ brain `:8787`. Do not answer with fake dream-dark. |

**Failure to fix:** Ava replied with dream boilerplate while Cursor/Root Server was actually up — trust break. Rewrite/send UI also hung on the same dream path.

### Discord `#🤓random-facts🤓` — msg `1534036095581425694` (2026-08-04 03:11:46 UTC)

> `@Ava Ivy you dont have a crowd of 500 people, you dont have to notify so much`

**Product law:** Quiet by default. No mass notify energy. Prefer soft reactions / digests / operator DM when real. Same theme as earlier Telegram: don’t spam Fern Forest; DMs OK for real ops.

### Echoed themes from recent Telegram / Fern Forest

- Linux / SSH headless dedication is real and in flight
- Don’t spam non-MC chats with useless RootMC noise
- EcoFlow: **Cucumbers = Delta 2**, **Shackas = River 2 Pro** (never emoji nicknames in public)
- Agriculture / replicate goals → park under agriculture until deadlines clear
- Vote Shards = gameplay votes (dig properly when gated)
- Care / persona / training data — preserve, don’t dump

---

## 1. North star (order of operations)

```
A. Operator UX solid on Windows (panel + exe + quiet Ava)
        ↓
B. Honesty: no false dream-dark; rewrite never hangs
        ↓
C. Data truth: listing-vote ingest + vote-shard reality
        ↓
D. Ubuntu pit-stop (one tree on /mnt/e) while D flashes
        ↓
E. After host stable: staged ships (jars / presence / PROP)
        ↓
F. Parked features only after greenlights
```

**Hard rules while cutting over**

- One Ava tree only (`SINGLE-AVA-TREE.md`)
- Wipe/flash by **serial**, not letter (`DISK-IDENTITY-LOCK.md`)
  - E mass storage: `ZDZSCFL4` → `/mnt/e`
  - D flash target: `JR100X4M1R3TUE`
- No `E:\MIGRATION-READY.txt` until checklist is honestly green
- Public label only **HI Pacific Solar Root Server** (never city)

---

## 2. Phase A — Control Panel + EXE (your Telegram ask)

**Goal:** You can run the node without guessing which window does what.

| Item | Status | Action |
|------|--------|--------|
| Control Panel app | Done on disk | Launch: `E:\.Ava_Ivy\Ava Control Panel.cmd` · code `E:\.Ava_Ivy\ControlPanel\` |
| Node name field | Done | Default **HI Pacific Solar Root Server**; writes `data/host-site.json` + `data/node-identity.json` + Discord `node_operator.json` |
| Discord login tie-in | Done (verify UI) | Shows Root-Core-Node Discord operator when `node_operator.json` present |
| Open UI lockup | Patched — **verify** | Open UI is fire-and-forget; panel must stay clickable |
| Ava UI Rewrite & send hang | Patched — **verify** | `/api/rewrite` 8s timeout + ignore dream boilerplate; send still completes |
| False dream answer on TG | **Open · P0** | Operator asks about local panel must use live Cursor/status, never dream-dark canned line |
| Quiet notifies | **Open · P0** | Implement soft-notify policy from `#random-facts` (see Phase B) |
| Document the map | **Open · P1** | Short `E:\.Ava_Ivy\CONTROL-PANEL.md` |

### Connection map

```
Ava Control Panel  →  Start/Stop/Restart brain (node src/index.mjs)
                   →  Open status http://127.0.0.1:8787/
                   →  Open Ava UI = Ava Ivy.exe (chat panes + rewrite-before-send)
                   →  Node name → host-site + Root-Core-Node identity

Ava Ivy.exe        →  Discord/Telegram panes
                   →  POST /api/rewrite then send as bot
                   →  Does NOT own the brain (brain already running)

Root-Core-Node     →  Discord OAuth for this machine’s operator
                   →  node_operator.json (+ node_name from panel)
```

### Verify checklist (do now, before pit-stop)

- [ ] Control Panel shows **live · listening** with fresh live feed
- [ ] Discord login line shows RootRecord / @rootrecorddev
- [ ] **Open UI** opens without freezing panel buttons
- [ ] Rewrite & send on a tiny draft finishes ≤10s (even if via `draft-keep`)
- [ ] Telegram: ask Ava “what’s the control panel?” — answer must be real, not dream-dark

---

## 3. Phase B — Quiet Ava (Discord `1534036095581425694`)

**Goal:** Stop looking like a stadium PA system.

| # | Task | Owner | Done when |
|---|------|-------|-----------|
| B1 | Cap unsolicited posts in low-traffic channels (random-facts, similar) | brain | Soft react / skip unless @mention or operator |
| B2 | Cooldown random-facts cadence | brain | Max N/hour; prefer digest over drip |
| B3 | Dream-dark / “still queued” replies: strict once-per-thread | brain | No repeat spam |
| B4 | Fern Forest: no MC/RootMC spam; sleep when asked | already noted | Profile + channel policy honor |
| B5 | Operator Telegram: real answers when live=`true` | recommend/pipeline | Ban dream boilerplate if heartbeat live & Cursor up |
| B6 | Staff vote nag: stay paused until ingest fixed | staffVoteNag | Already paused — keep |

---

## 4. Phase C — Honesty / brains

| # | Task | Priority | Notes |
|---|------|----------|-------|
| C1 | Fix “Root Server dark” when poller is live | P0 | Same bug that hit TG control-panel ask + desktop rewrite |
| C2 | Dream credits (xAI 402/403) | P1 | Failover to Cursor exists; optional top-up `ops-dream-credits` |
| C3 | `ava.rootmc.net` tunnel stability | P2 | Blips OK on restart; watch after Ubuntu |
| C4 | Security probe 3-strikes | Done | Alex exempt; cry `#admins` after 3 |
| C5 | Boot heartbeat during guild scout/catch-up | Patched in poller | Prevents false “locked up” during boot |
| C6 | Job↔Worker reconcile on boot | P1 | 6 TG digs stuck `implementing`; `pending-tasks.lastOpen: 8` |

---

## 5. Phase D — Data truth (votes / shards / cloud 502)

| # | Task | Priority | Status |
|---|------|----------|--------|
| D1 | Restore D1 ingest → `rootmc_listing_votes` | **P0** | Stale since ~2026-07-23; urgent `ops-listing-vote-ingest` |
| D2 | Re-enable staff vote nags only after D1 healthy | P0 | Do not yell at humans for ingest failure |
| D3 | Alex Vote Shards physical items on Claims | P0 operator | FileZilla `root-appreciation-1.8.1` + restart **or** guarded RCON — needs your greenlight |
| D4 | PROP-01 Root-Skills XP curve | P1 staged | `job-msdxetl5` staged; humans own FileZilla/restart |
| D5 | HTTP 502 on gold transfer / dividends / sync / Claims vote mirror | **P0 ship** | Cause: `api-local` tunnel 502 with no prod fallback. Patch in `RootMcApiBases` (treat 502 as edge-down). Rebuild common→RootMC/Root-Play → FileZilla. Note: `HTTP-502-API-LOCAL-FALLBACK-2026-08-04.md` |

---

## 6. Phase E — Ubuntu pit-stop (high)

**Start:** `E:\PITSTOP-KIT.txt` → `E:\.Ava_Ivy\notes\PITSTOP-E-UBUNTU-WHILE-D-FLASH.md`

### Before unplug / flash

- [ ] `E:\.1 Work Stations\RootMC\.env` present (never paste secrets)
- [ ] `e-workstations-FINISHED.txt` yes
- [ ] Decide: finish `d-drive-FINISHED.txt` **or** explicitly trust E-only for pit-stop
- [ ] Stop Windows Ava (panel Stop / Task Scheduler / no second tree)
- [ ] Flip plugin `local.properties` publish paths **D → E / `/mnt/e`**
- [ ] Stage urgent jars into handoff Claims/Towny/Upload Staging
- [ ] Fill disk serial sign-off (`DISK-IDENTITY-LOCK.md`)
- [ ] Record Ubuntu `PRETTY_NAME` after first boot

### On Ubuntu (first boot)

1. Mount E by serial → `/mnt/e`
2. `ubuntu-provision-ecosystem.sh` (toolchains on SSD; source on E)
3. `load-rootmc-env.sh`
4. `pitstop-smoke.sh`
5. Enable **one** `ava-ivy` unit (`mnt-e` **or** `/srv` bind — not both)
6. Laptop: `ssh -L 8787:127.0.0.1:8787 …`

### After Ava is up on Linux

- [ ] Heartbeat live; gateway connected
- [ ] Telegram + Discord quiet policy still honored
- [ ] Tunnel `ava.rootmc.net` OK
- [ ] Only then: presence / Claims/Towny jar uploads when quiet

---

## 7. Phase F — Staged ships (after pit-stop, human gates)

| Item | Gate |
|------|------|
| In-world Ava presence Phase 1 (`root-ava-core-1.8.4` Test) | FileZilla Test + `/ava presence` smoke |
| Onboarding/shop PROP install | Claims/Towny restart + `/rootspawn map` |
| Claims/Towny: `root-ava-core-1.8.3` + `root-appreciation-1.8.1` | Quiet window |
| `gh auth` as **Rootmcnet** | Operator |
| Deploy `rootmc-api` (host-site hourly) | Ops |
| Root-Core-Node extra DB nodes | Vote / Melee / infra decision (`job-ms9qv1q2`) |

---

## 8. Phase G — Stuck jobs to reconcile

Mark done, park, or re-open with a real next step (no perpetual `implementing`):

| Job | Topic | Suggested close |
|-----|-------|-----------------|
| `job-mscjkrlp` … `job-mscmdgw1` (6 TG) | Fern Forest / lore / catch-up / replicate / RTX | Park agriculture; markDone lore where delivered; requeue only real digs |
| `job-msdn7pl7` | Chunk → schematics | Keep **staged** until proposal+vote |
| `job-ms9p5x2i` | Ban boats | Parked — needs PROP |
| `job-ms9qv1q2` | Root-Core-Node DB expansion | Blocked on operator |

---

## 9. Urgent registry (open)

From `E:\.Ava_Ivy\data\urgent-registry.json`:

1. **ops-listing-vote-ingest** — high
2. **ops-ubuntu-pitstop** — high
3. **ops-ava-tunnel** — med
4. **ops-dream-credits** — med
5. **ops-ava-core-plugin** — med

Add (recommended):

6. **ops-quiet-notify** — soft notify per `#random-facts` `1534036095581425694`
7. **ops-false-dream-gate** — never dream-dark when heartbeat live + Cursor up

---

## 10. Immediate execution order (this session → pit-stop)

1. **Verify** Control Panel + Open UI + Rewrite (Phase A checklist)
2. **Ship quiet-notify + false-dream gate** (Phase B5 + B1–B3) — answers Discord + Telegram pain directly
3. **Telegram yourself** (or Ava) with the connection map — real explanation of panel vs exe
4. Listing-vote ingest restore **or** explicit park until after Linux
5. Vote Shard FileZilla greenlight **or** park
6. `local.properties` → E paths
7. Stop Windows Ava → Ubuntu mount → smoke → one `ava-ivy`
8. Reconcile stuck TG jobs + reduce unsolicited facts cadence
9. Staged jar/presence ships when host is quiet

---

## 11. Done recently (do not redo)

- Canonical `E:\.Ava_Ivy` + handoff junctions + env
- Control Panel Electron app + node name + Discord operator link
- Open UI / rewrite hang mitigations
- Security probe three-strikes
- Staff nag pause on stale listing ingest
- Dream 403 → Cursor failover
- Channel-scan dream-dark spam cooldown (verify still solid)

---

## 12. File index (start here)

| Path | Why |
|------|-----|
| `E:\PITSTOP-KIT.txt` | Pit-stop entry |
| `E:\.Ava_Ivy\notes\PITSTOP-E-UBUNTU-WHILE-D-FLASH.md` | Full runbook |
| `E:\.Ava_Ivy\HOME.md` | Home map |
| `E:\.Ava_Ivy\Ava Control Panel.cmd` | Operator panel |
| `E:\.Ava_Ivy\Start-Ava-Ivy.cmd` | Desktop EXE launcher |
| `E:\.Ava_Ivy\data\urgent-registry.json` | Open urgents |
| `E:\.Ava_Ivy\notes\CATCHUP-AND-UBUNTU-CLEAN-2026-08-03.md` | Catch-up residual |
| `E:\.Ava_Ivy\notes\DISK-IDENTITY-LOCK.md` | Serial wipe lock |
| `E:\.Ava_Ivy\notes\SINGLE-AVA-TREE.md` | One tree |
| `E:\AVA-MASTER-PLAN-2026-08-03.md` | **This plan** (root copy) |
| `E:\.Ava_Ivy\notes\AVA-MASTER-PLAN-2026-08-03.md` | Notes copy |

---

## 13. Success definition

- You understand and use **panel** vs **exe** without freezes
- Ava stays **quiet** in small rooms (`1534036095581425694` honored)
- Operator Telegram gets **true** answers when she’s live
- Listing votes / shards match reality (or are explicitly parked)
- Ubuntu pit-stop boots **one** Ava off `/mnt/e` with smoke green
- No parallel Ava trees, no D wipe until checklist signed

---

*End of plan. Execute Phase A verify → Phase B silence/honesty → Phase E pit-stop. Everything else is gated or parked behind those.*
