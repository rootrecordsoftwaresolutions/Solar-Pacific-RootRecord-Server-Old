# RootMC — Operations Report & Work Order

**Report period:** last ~24 hours (Discord + in-game)  
**Report date:** 2026-07-02 (HST)  
**Status:** **Implemented in source** — pending jar build + Shockbyte deploy  
**World:** `world`

Staff-facing work order, server status, player feedback, and dev backlog derived from Discord staff chat and `/feedback` submissions.

---

## Executive summary

Launch day +1 on a **new map** with **all Towny claims wiped** (orgs/banks kept). Player retention is the main pain: new players left after death (**no `/back`**), staff **100 G handouts** did not stick, and towns *look* like they “fall” because mayors have not reclaimed on the new geography. One **`/buy`** bug report needs reproduction. Staff submitted **one `/feedback`** asking for **24h keep-inventory** and **24h RTP** grace for new players — aligns with retention work already discussed in Discord.

---

## Server status (current)

| Area | Status | Notes |
|------|--------|-------|
| **Map / world** | Fresh `world` live | Claims/spawns/homes cleared via fresh-world reset; town names + banks in MySQL |
| **Towny deletion** | Not aggressive | Daily taxes **off**, town upkeep **0 G**, inactive resident purge **off**, bankruptcy does **not** auto-delete towns |
| **Town ruins** | Active | Empty town → ruin up to **72h**, then delete (`empty_towns_become_ruins: true`) |
| **Economy** | Market reset | Public D1 market reset; chest shops need rebuild/reprice (**A_town** called out) |
| **Loans** | Rank-based cap | Default **100 G**; rank price = max borrow (`root-loans 1.0.8`) |
| **Death** | PvP fee active | **10%** victim balance → treasury/killer split (`rootmc.yml`) |
| **`/back`** | **Implemented** | `root-essentials 1.4.41` |
| **RTP** | **Implemented** | `/rtp` during 24h grace only |
| **24h keep inventory** | **Implemented** | FB-001; death tax skipped in grace |
| **Wilderness alerts** | **Implemented** | Value-scaled builder warnings + plugin bridge |
| **Loan cap = balance** | **Implemented** | `max-cap-mode: min_both` in root-loans 1.0.9 |
| **Embassy pricing** | **Configured** | Towny embassy plot cost **100 G** |
| **Map return grant** | **Implemented** | **1000 G** one-time via **`/rootmc claim-return`**; requires **`/link`**; returning players only (`rootmc 1.3.37`) |

Reference: [`Server Files (Handoff Off)/FRESH-WORLD-START.md`](../Server%20Files%20(Handoff%20Off)/FRESH-WORLD-START.md)

---

## Discord timeline (staff + community)

| Time (HST) | Who | Summary |
|------------|-----|---------|
| 06:28 | RootRecord [GDev] | Good morning |
| 06:30 | ZuppaFredda | Good morning |
| 06:31 | RootRecord [GDev] | Low battery / timing for hike |
| 06:40 | I'm Just Existent [zZzZ] | “Towns fall too quickly” |
| 06:54 | Alexrs94 [GDev] | Explained: **26.2 migration** / fresh launch context |
| 08:43 | RootMC APP | **Svenj9** completed Minecraft verify — linked |
| 11:06 | I'm Just Existent | Acknowledged (“oh”) |
| 12:20 | ZuppaFredda | **`/feedback`** — 24h keep inventory + 24h RTP for new players *(see below)* |
| 12:29 | ZuppaFredda | **Staff day report** — 3 new players, 2 left, `/back`, 100 G grants, khourzh `/buy`, points to feedback |
| 13:45 | Alexrs94 [GDev] | Welcome message; launch was yesterday; new map intentional; post-hike |
| 13:51 | RootRecord [GDev] | “They will return” |
| 13:59 | RootRecord [GDev] | **Dev direction** — embassies, wilderness build warnings, plugin bridges, loans, chest shops |
| 14:00 | Alexrs94 [GDev] | Personal (supplies run) |

---

## In-game feedback (last 24h)

### FB-001 — New-player grace window

| Field | Value |
|-------|-------|
| **Source** | `/feedback` |
| **Player** | ZuppaFredda |
| **UUID** | `d24c48ed-a6af-4827-bd37-c868be39a50d` |
| **Server ID** | `15bbc057-4f8b-4761-abdb-7b7e4d9c7512` |
| **World** | `world` |
| **Submitted** | 2026-07-02 ~12:20 HST |
| **Message** | lets add a 24h keep inventory for new players and a 24h rtp for new players |

**Interpretation:** First-day onboarding buffer so deaths and wilderness exploration do not instantly churn newcomers. Directly related to the friend who **died, found no `/back`, and quit**.

**Current codebase:** No RTP command; Towny keep-inventory flags all **false**; RootMC PvP death tax applies regardless of account age.

**Proposed implementation (Root-Essentials or small `root-onboarding` module):**

1. Track `first_join_at` per UUID (MySQL or YAML under `plugins/RootRecord/`).
2. **24h keep inventory:** `PlayerDeathEvent` — if `now - first_join < 24h`, set keep inventory + keep level (optionally skip PvP death tax sweep for grace window — product decision).
3. **24h RTP:** `/rtp` (or `/wild`) — random safe location in `world` wilderness, cooldown e.g. 15–30 min, only while grace active; warm-up matches existing teleport config.
4. On join: action-bar once — “New player grace: keep inventory & RTP for 24h.”
5. Config keys in `root-essentials.yml`: `new-player.grace-hours: 24`, `new-player.keep-inventory: true`, `new-player.rtp-enabled: true`.

---

## Staff field report (ZuppaFredda — 12:29 HST)

| Item | Detail | Action |
|------|--------|--------|
| New players | **3** joined | Monitor return visits |
| Retained | **1** may stick | — |
| Churn | **2** left | Friend: death + **no `/back`**; “promising” player also left |
| Economy ops | **100 G × 3** to start towns | **Stop handouts** — sell **embassy plots** (~100 G) instead (GDev) |
| Bug | **khourzh** — `/buy` problem after joining Zuppa’s town | Reproduce steps; check post–market-reset listings |
| Direction | Staff agrees with **feedback** approach | See FB-001 + work orders below |

---

## Player / community signals

| Signal | Likely cause | Response |
|--------|--------------|----------|
| “Towns fall too quickly” | Fresh map: **zero claims** until mayors reclaim; solo towns look dead | Player FAQ + `#updates` reclaim reminder |
| New player death → quit | **`/back` missing** + no keep inventory | WO-001, WO-002 |
| Discord link | **Svenj9** verified | Normal onboarding |

---

## Work orders

Priority: **P0** = retention/blocker · **P1** = this sprint · **P2** = planned · **P3** = ops

### P0 — Retention / blockers

| ID | Title | Owner | Notes |
|----|-------|-------|-------|
| **WO-001** | Implement **`/back`** in Root-Essentials | Dev | `PlayerStateService.rememberBack()` exists; wire death/teleport + command + permission `rootessentials.back` |
| **WO-002** | **24h new-player grace** (keep inv + RTP) | Dev | FB-001; config-driven; see proposal above |
| **WO-003** | Reproduce **khourzh `/buy` bug** | Staff/Dev | Get item, shop, error message; check `rootmc-shops` split-buy + stale stock after market reset |

### P1 — Onboarding & economy (GDev direction)

| ID | Title | Owner | Notes |
|----|-------|-------|-------|
| **WO-004** | **Embassy sales** instead of 100 G grants | Staff + Dev | Towny embassy plots; mayor pricing ~100 G; staff policy: no free town-start grants |
| **WO-005** | **Wilderness build warnings** to the builder | Dev | Periodic notifications in true wilderness; intensity ∝ `itemPrice()` (market → worth fallback) |
| **WO-006** | **Block-event plugin bridge** | Dev | Extend `rootrecord-common` pattern (`ShadedServiceBridge`) for wilderness block break/place callbacks |
| **WO-007** | Player FAQ / MOTD — **fresh map reclaim** | Staff | “Town orgs kept; reclaim plots on new `world`” |

### P2 — Economy follow-ups

| ID | Title | Owner | Notes |
|----|-------|-------|-------|
| **WO-008** | **Loan cap = full balance** (optional mode) | Dev | Today: rank price cap (`root-loans`); add `min(balance, rankCap)` or balance-only mode |
| **WO-009** | **A_town chest shop rebuild** | Staff (A_town) | Re-create signs, reprice vs post-reset market; verify D1 sync |
| **WO-010** | Deploy **unreleased plugin jars** | Ops | root-territories 1.2.11, root-essentials 1.4.40, root-chamber 1.0.0 — see SERVER-CHANGELOG |

### P3 — Ops / comms

| ID | Title | Owner | Notes |
|----|-------|-------|-------|
| **WO-011** | Discord **#updates** post — launch + reclaim + **1000 G return grant** | GDev | `/link` then `/rootmc claim-return`; builds moved from old map |
| **WO-012** | PlaceholderAPI on 26.2 | Ops | Remove hytale jar; use `PlaceholderAPI-2.12.3-DEV-268.jar` |

---

## Dev backlog detail (from GDev — 13:59 HST)

1. **Sell embassies, not grants** — New players can earn ~100 G; most join existing protection anyway.
2. **Wilderness build notifications** — Escalate messaging as placed/broken block value rises (market-priced).
3. **Cross-plugin bridge** — Decouple block-destroy/place hooks from per-plugin command logic.
4. **Loans** — May soon cap at **entire wallet balance** (public market + reset context).
5. **Chest shops** — Market reset; shops must be recreated, revalued, distributed (**A_town**).

---

## Known gaps (codebase audit)

| Feature | Expected | Actual |
|---------|----------|--------|
| `/back` | Root-Essentials cutover smoke test | Not in `plugin.yml`; no player command |
| RTP | FB-001 | No plugin/command |
| 24h keep inventory | FB-001 | Towny flags off; no first-join grace |
| Wilderness alerts | Notify **residents** of intrusion | Implemented (`root-territories`); does **not** warn **builder** in wilderness |
| Embassy pricing | Paid onboarding | Towny `embassy` plot cost **0** in config |

---

## Suggested staff replies (Discord)

**Towns “falling”:**

> Launch was yesterday on a **new map** (Paper 26.2). We kept town names and banks, but **all claims were reset** — mayors need to reclaim on the new terrain. Towns aren’t being deleted by upkeep (that’s off). Solo towns can look empty until someone reclaims.

**New players / death:**

> We’re adding **`/back`** and a **24h new-player grace** (keep inventory + RTP) based on staff feedback. Death in PvP still has a gold fee after grace ends.

---

## Deploy checklist (when WO-001 / WO-002 ship)

1. Bump `root-essentials` version in `build.gradle.kts`.
2. `publishPlugins` → handoff + web manifest.
3. Update `plugins/RootRecord/root-essentials.yml` with grace + RTP settings.
4. FileZilla jar to Shockbyte; restart.
5. Smoke: `/back` after death; `/rtp` within 24h of first join; grace expiry after 24h.
6. Update [`plugins/root-essentials.md`](plugins/root-essentials.md) + [`SERVER-CHANGELOG.md`](SERVER-CHANGELOG.md).

---

## References

- Fresh world procedure: `Server Files (Handoff Off)/FRESH-WORLD-START.md`
- Towny live config: `Server Files (Handoff Off)/plugins/Towny/settings/config.yml`
- Feedback command: `plugins/roothelp/.../FeedbackCommand.java` → API → Discord `#ingameFeedback`
- Wilderness alerts: `plugins/root-territories/.../WildernessAlertService.java`
- Loan caps: `plugins/root-loans/.../RankLoanLimitService.java`

---

*Generated from Discord staff chat (2026-07-02), ZuppaFredda `/feedback`, and codebase audit. Gold not dollars in all player-facing copy.*
