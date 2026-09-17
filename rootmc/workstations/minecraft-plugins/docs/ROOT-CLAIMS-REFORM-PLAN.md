# RootClaims reform plan

Status: **implemented** (2026-07-23).  
Terminology: **area** = group of up to 10 claim circles (was “camp”).

Plugin: `plugins/root-claims/`  
Config: `root-claims.yml`  
Handoff: `1. RootMC - Claims/`

---

## Goals

Replace the flat “up to 64 independent circles” model with **up to 3 areas**, each with a **level 1–10** expansion tree, named areas, and clearer member rules.

---

## Model

```
Player
 ├── Area 1  (root) ── expansions…  (level = circle count, max 10)
 ├── Area 2  (root) ── expansions…
 └── Area 3  (root) ── expansions…
```

| Concept | Meaning |
|---|---|
| **Area** | A named group of claim circles owned by one player. Max **10** circles (level). Has one **spawn** and one **area bank**. |
| **Root** | First circle that founds the area. |
| **Expansion** | Extra circle snapped to the rim of a circle in the same area. Paid from **that area’s bank**. |
| **Level** | Number of circles in the area (1 = root only, 10 = full). |
| **Member** | Player on the area’s access list (`/c add`, `/c trust`, `/c friend` — same list). |

Circles stay **16-block radius**. Territory buffer (**+48**) and stranger no-claim rules stay as today unless called out below.

---

## Limits

| Limit | Value |
|---|---|
| Areas per player | **3** |
| Circles per area (level) | **1–10** |
| Next-area unlock | Must own **(N−1)** areas at **level 10** before founding area **N** (N>1) |
| Max circles per player | **30** (3 × 10) |
| Memberships on others’ areas | **Unlimited** |
| Radius | **16** |
| Edge snap tolerance | **3** |
| Territory buffer | **48** |

A member of someone else’s area may still own up to 3 of their own areas.

---

## Economy

### Founding an area (wallet)

| Area slot | Cost | Unlock |
|---|---|---|
| 1st area | **75 G** | Always available |
| 2nd area | **1000 G** | Requires **1** area at level 10 |
| 3rd area | **10000 G** | Requires **2** areas at level 10 |

- Paid from **player wallet**.
- **Progression gate:** `/c new` for slot 2+ fails unless enough areas are already level 10 (see table). Message should say how many level-10 areas you still need.
- First-area bank seed: keep current behavior unless changed later (**25 G** of the 75 into the new area bank; remainder → Server Reserve as tax-free service fee).
- 2nd / 3rd areas: full fee → reserve (no bank seed unless we decide otherwise at implement time). Confirm at implement: seed only on area 1.

### Expanding an area (area bank)

| From → to level | Cost |
|---|---|
| 1 → 2 | **75 G** |
| 2 → 3 | 75 × 1.5 = **112.5 G** |
| 3 → 4 | × 1.5 = **168.75 G** |
| … | **75 × 1.5^(targetLevel − 2)** for target level ≥ 2 |
| Cap | Level **10** (cannot expand further) |

- Paid from **that area’s bank** (`/c bank deposit`).
- Must stand on an eligible rim (±3); anchor snaps to edge (current behavior).
- Parent for payment / genealogy = the area root (or keep per-circle parent links; bank is always the **area bank**).

### Unclaim refund

| Rule | Value |
|---|---|
| Refund percent | **50%** |
| Area founding fee refund | 50% of founding fee (net of bank-seed policy — define at implement) → wallet |
| Expansion fee refund | 50% → **area bank** |
| Remaining bank balance | Return to owner wallet on full area teardown (same idea as today’s unclaim-all bank return) |

---

## Members

| Command | Effect |
|---|---|
| `/c add \| trust \| friend <player>` | **Same list** — add **member** |
| `/c untrust` (and optionally `/c remove`) | Remove member |

**Member rights** (unchanged intent from today’s trusted list):

- Build / interact / containers inside the claim
- Community chest
- Area spawn (even if private)
- Territory ally (no satellite alerts; wilderness destroy fee exempt in buffer)
- May place expansions on that area’s rim (if `allow-trusted-expansion` stays true) — new circle is owned by the **acting player** only if we keep today’s ownership rule; **decide at implement**: expansions by members should attach to **host area** and stay under host ownership (preferred for area level), not create a new area for the member.

**Preferred ownership rule for reform:** only the **area owner** (or admin) creates circles that raise area level; members get build/ally rights but do **not** spend the host bank to expand unless we explicitly allow “member may expand host area.” Default proposal: **owner-only expansion**; members do not raise level. Flag for confirm at implement if today’s `allow-trusted-expansion` should be removed or reinterpreted.

---

## Naming

| Phase | Behavior |
|---|---|
| **Now (MVP)** | New areas auto-named `username(1)`, `username(2)`, `username(3)` by slot |
| **Commands** | `/c new` — found next area with auto name; `/c new <areaname>` — found with custom name (validate uniqueness per owner) |
| **Later** | Owner renames area in claims GUI |

Also keep `/c claim` as alias of `/c new` where it makes sense, or document migration in help text.

---

## Spawn routing (`/spawn`)

Today `/spawn` (Root-Essentials) only goes to **server spawn**. Reform: one command for server + area visits.

| Command | Behavior |
|---|---|
| `/spawn` | Server spawn (unchanged) |
| `/spawn <player>` | That player's area spawn (if **public** or caller is **owner/member**) |
| `/spawn <areaname>` | By area display name (same access rules) |
| `/spawn <player> <areaname>` | Disambiguate when a player has multiple areas (optional MVP+) |

**Access:** same as today’s claim spawn — public toggle, else owner/members only.

**Multi-area default for `/spawn <player>`:** prefer public area; else first area the caller may use (member/owner). Message if none allowed.

**Wire-up:** extend Essentials `SpawnCommand` to call into Root-Claims (soft depend / service), or register arg handling from claims. Keep `/c spawn` as thin alias for muscle memory.

---

## Commands (target)

| Command | Purpose |
|---|---|
| `/c` | Dashboard |
| `/c new` | Found next area (auto name) |
| `/c new <name>` | Found next area with name |
| `/c claim` | Expand current area (rim) **or** alias of new — pick one at implement; prefer **expand** vs **new** split |
| `/c add / trust / friend` | Member add |
| `/c untrust` | Member remove |
| `/c bank` / `deposit` | Area bank |
| `/spawn` | Server spawn (no args) |
| `/spawn <player\|area>` | Visit that player's / area's spawn (public or member) |
| `/c set spawn` | Set area spawn (owner; inside area) |
| `/c spawn` | Alias → `/spawn` / `/spawn <self>` (optional keep for habit) |
| `/c unclaim` | Remove circle; if last circle in area, tear down area |
| `/c unclaim all` | Tear down all owned areas |

---

## Data model changes (implement notes)

- Introduce **area id** (or treat root `ClaimKey` as area id; expansions store `areaRootKey` / `parentKey`).
- Persist **display name** on the area (root).
- Persist **area level** (or derive = count of circles in that area).
- Config knobs:

```yaml
limits:
  max-areas-per-player: 3
  max-level-per-area: 10

economy:
  area-prices-g: [75, 1000, 10000]
  first-area-bank-seed-g: 25
  expansion-base-price-g: 75
  expansion-multiplier: 1.5
  unclaim-refund-percent: 50
```

- Migrate existing stores: each current circle without parent → area named `owner(n)`; children keep parent links; clamp or warn if >3 areas or >10 per area.

---

## Out of scope (later)

- GUI rename flow (hook only: store name now)
- Splitting `/add` vs `/trust` into different privilege tiers (explicitly **not** in this reform)
- Changing radius / territory buffer / BlueMap / outlines (keep unless needed for area coloring)

---

## Implement checklist

1. Config + price helpers (area slot prices; expansion `75 * 1.5^(level-1)` for next level).
2. Area root tracking, level cap 10, max 3 areas.
3. Charge founding from wallet; expansions from **area bank**.
4. Names: auto `username(1..3)`; `/c new` / `/c new <name>`.
5. Refund percent → **50%**; messages + unclaim-all math.
6. Help / dashboard / settings copy (areas, levels, prices).
7. `/spawn <player|area>` routing in Root-Essentials ↔ Root-Claims; `/c spawn` alias.
8. Migration path for existing `claims.yml` data.
9. Handoff `root-claims.yml` under `1. RootMC - Claims` when shipping.

---

## Open confirm at implement (small)

Resolved defaults used in implementation:

1. Bank seed **only** on 1st area (75 → 50 reserve + 25 bank).
2. Member expansion: **owner-only** (`allow-trusted-expansion: false`).
3. `/c claim` = expand; `/c new` = found area.
4. `/spawn <player>` public-first among accessible areas.

---

## Changelog when shipping

Update `Change Logs/` + player-facing help; Gold not dollars; no Discord post unless asked.
