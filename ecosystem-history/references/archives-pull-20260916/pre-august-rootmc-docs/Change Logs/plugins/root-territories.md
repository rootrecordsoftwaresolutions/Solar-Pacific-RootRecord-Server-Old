# root-territories

Nation/town influence and BlueMap outlines.

**Source:** `Plugin Building/Minecraft/plugins/root-territories/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.3.4] â€” 2026-07-05

### Fixed
- **Nation/town border particles at spawn** â€” red/blue influence rings no longer draw inside the spawn no-build zone (walls + grief buffer). Nation borders route around the buffer instead of tracing the wall line.

**Deploy:** `root-territories-1.3.4.jar` â†’ `/territories reload`

---

## [1.3.3] â€” 2026-07-05

### Fixed
- **Spawn grief ring on BlueMap** â€” green no-build zone now traces the same distance-based buffer as in-game (`inside walls` âˆª `â‰¤20 blocks from wall edge`). Replaces broken miter offset on the concave ridge wall polygon.

**Deploy:** `root-territories-1.3.3.jar` â†’ `/territories reload` (or `/rootspawn reload` if Root-Spawn is present)

---

## [1.3.2] â€” 2026-07-05

### Changed
- **BlueMap spawn overlay** â€” green fill/ring matches **no build/break** zone (walls + `grief_buffer_blocks` from `root-spawn.yml`).
- Yellow inner line = PvP wall boundary.
- Seeds `spawnarea-refined.txt` from Root-Spawn jar when missing or legacy origin ring.

**Deploy:** `root-territories-1.3.2.jar` + `root-spawn-1.4.11.jar` â†’ `/territories reload`

---

## [1.2.11] â€” 2026-07-02

### Fixed
- **Town wilderness alerts** â€” same-nation residents no longer trigger â€œstealing resourcesâ€ in another townâ€™s wilderness ring (e.g. Moreni member in A Townâ€™s influence zone). Previously only same-town was exempt.

**Deploy:** `root-territories-1.2.11.jar` â†’ restart or `/territories reload`.

---

## [1.2.10] â€” 2026-07-02

### Fixed
- **Wilderness alerts** â€” same-nation or same-town players no longer trigger false â€œstealing resourcesâ€ warnings when Towny resident lookup failed (now resolves resident by player, UUID, then name).

**Deploy:** `root-territories-1.2.10.jar` â†’ restart or `/territories reload`.

---

## [1.2.9] â€” 2026-07-01

### Removed
- **Mesa badlands** â€” biome indexing, hunger/mob multipliers, claim blocking, border messages, BlueMap overlays, `/territories badlands` commands, and `badlands-cells.txt` persistence.

**Deploy:** `root-territories-1.2.9.jar` + updated `root-territories.yml` â†’ restart or `/territories reload`. Delete `plugins/RootRecord/badlands-cells.txt` on the server if present.

---

## [1.2.8] â€” 2026-06-30

### Fixed
- **Mesa map outlines** â€” large badlands patches no longer draw only half the biome on BlueMap. Contour generation now buckets big cell groups and always tiles marching-squares passes (fixes partial polygons when a single full-bbox trace succeeded but was incomplete).
- **Biome indexing** â€” denser chunk sampling (every block) and deeper surface biome checks so eroded/wooded badlands edges index reliably.

### Changed
- **scan-bounds** `max-x` **4800**, `max-z` **3200** in `root-territories.yml` (was 3079 / 2978).

**Deploy:** `root-territories-1.2.8.jar` + updated `root-territories.yml` â†’ restart or `/territories reload`, then **`/territories badlands reindex`** (watch console until outlines rebuild).

---

## [1.2.7] â€” 2026-06-28 (baseline)

_Changelog tracking started. Prior release history not backfilled._
