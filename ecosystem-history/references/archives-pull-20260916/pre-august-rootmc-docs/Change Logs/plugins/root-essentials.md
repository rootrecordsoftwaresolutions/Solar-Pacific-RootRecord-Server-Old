# root-essentials

EssentialsX replacement, treasury, warps.

**Source:** `Plugin Building/Minecraft/plugins/root-essentials/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.7.12] â€” 2026-07-31

### Changed
- **`/explode`** â€” requires **TNT worn as chestplate** (Paper equippable stamp on TNT stacks). Confirm still wipes wallet to Reserve, keeps inventory except the vest, half-heart + Slowness nearby, town/wilderness broadcast.

**Deploy:** `root-essentials-1.7.12.jar` + updated `root-essentials.yml` messages â€” Claims **and** Towny; restart.

---

## [1.7.11] â€” 2026-07-31

### Added
- **`/explode`** â€” costs 1 TNT; confirm prompt warns of full wallet loss; `/explode confirm` seizes wallet to Server Reserve, keeps inventory (except the TNT), kills the runner, leaves players within 5 blocks at half a heart + Slowness 1m, and broadcasts a town/wilderness announcement.

**Deploy:** `root-essentials-1.7.11.jar` + updated `root-essentials.yml` â€” Claims **and** Towny; restart.

---

## [1.7.9] â€” 2026-07-26

### Removed
- **`/back`** â€” unreliable return teleport; use `/home`, `/spawn`, or `/rtp` instead.

---

## [1.6.45] â€” 2026-07-24

### Changed
- **`/pay reserve` credits Server Reserve** â€” wallet â†’ vault + `DONATION` ledger inflow (was `NOTE_BURN` with no reserve credit). Full amount stays in Reserve (not shared into the 25% bond coupon pool).

**Deploy:** `root-essentials-1.6.45.jar` â€” restart Claims + Towny. API: deploy `rootmc-api` so site ledger labels match.

---

## [1.4.76] â€” 2026-07-10

### Fixed
- **Bond pool covers all reserve deposits** â€” every treasury credit path (tax, death fees, Towny sinks, loan repayments, bond issuance, forfeited coupons, etc.) now feeds the bond day inflow pool through a single dispatcher wired at Essentials startup.
- **Transaction tax inflow** â€” withhold tax credits now notify bonds (previously only some paths did).

**Deploy:** `root-essentials-1.4.76.jar` + `root-bonds-1.0.15.jar` â€” restart.

---

## [1.4.75] â€” 2026-07-10

### Fixed
- **Bond income bridge** â€” Server Reserve inflows now notify Root-Bonds through a classloader-safe service instead of the shaded static hub. Death fees, taxes, and loan repayments accrue toward the 25% bond coupon pool again.

**Deploy:** `root-essentials-1.4.75.jar` + `root-bonds-1.0.14.jar` â€” restart.

---

## [1.4.41] â€” 2026-07-02

### Added
- **`/back`** â€” return to location before teleport or death (remembers on all Root-Essentials teleports).
- **New-player grace (24h)** â€” keep inventory on death, skip PvP death tax during grace, `/rtp` random wilderness teleport (15m cooldown).
- **Wilderness build warnings** â€” action bar + chat for high-value blocks in Towny wilderness; intensity scales with market/worth price.
- **Plugin bridges** â€” `RootMcWildernessBlockNotifier` + `RootMcNewPlayerGrace` in `rootrecord-common` for cross-plugin hooks.

### Changed
- **MOTD** â€” fresh-map reclaim reminder, grace commands, embassy ~100 G guidance.

**Deploy:** `root-essentials-1.4.41.jar` + updated `root-essentials.yml` (creates `root_player_first_join` table) â€” restart.

---

## [1.4.40] â€” 2026-07-02

### Added
- **PAPI baltop ranks** â€” `%rootessentials_baltop_town_1%` â€¦ `_10%`, same for `nation` and `player`; matches `/baltop` (RootMC economy DB, not TownyAdvanced).

**Deploy:** `root-essentials-1.4.40.jar` + `DecentHolograms/holograms/baltoptownsnations.yml` â€” restart, `/dh reload`.

---

## [1.4.39] â€” 2026-07-02

### Fixed
- **PlaceholderAPI registration** â€” `loadafter: [PlaceholderAPI]` and extra retry for balance holograms.

**Deploy:** `root-essentials-1.4.39.jar` â€” restart Paper.

---

## [1.4.38] â€” 2026-07-02

### Fixed
- **Treasury founding dedupe** â€” 3-second debounce on `towny:new-town` / `towny:new-nation` sinks so duplicate Towny events do not triple-count reserve intake.
- **Admin teleport back** â€” `rememberBack` exposed for root-admin compatibility after teleport commands.

**Deploy:** `root-essentials-1.4.38.jar` â€” restart Paper.

---

## [1.4.33] â€” 2026-06-28

### Fixed
- **Economy conservation:** survey fee only sinks to treasury after a successful report (failed runs refund wallet only).
- **Towny founding tags:** nation/town event hooks try multiple Towny class names; 400 G / 2,000 G deposits re-tagged as `towny:new-town` / `towny:new-nation` when hooks miss (no new gold â€” correct ledger descriptor).

**Deploy:** `root-essentials-1.4.33.jar` â€” restart Paper.

## [1.4.29] â€” 2026-06-28

### Fixed
- **Towny treasury tags:** defer channel clear to next tick so founding deposits are not mis-tagged as claims; improved stack-trace inference for `PreNewTownEvent` / `PreNewNationEvent`.

**Deploy:** `root-essentials-1.4.29.jar` + **rootmc-realm-api** (ledger display reclassifies 400 G / 2,000 G founding rows).

## [1.4.28] â€” 2026-06-28

### Fixed
- **Vote rewards** now route through `depositIncome`, so active loans receive the 100% income sweep (same as `/mint`, shop sales, and transfers). Treasury debits without pre-crediting the wallet; loan repayment runs before wallet deposit.

**Deploy:** `root-essentials-1.4.28.jar` + `root-rewards-1.0.9.jar` â€” restart Paper.

## [1.4.26] â€” 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
