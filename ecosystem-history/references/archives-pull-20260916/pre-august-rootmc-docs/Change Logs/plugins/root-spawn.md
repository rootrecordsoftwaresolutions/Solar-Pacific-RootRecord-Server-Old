# root-spawn

Spawn ring and staff mapping tools.

**Source:** `Plugin Building/Minecraft/plugins/root-spawn/`

---

## [1.4.11] — 2026-07-05

### Changed
- `/rootspawn reload` refreshes BlueMap spawn markers via Root-Territories.

**Deploy:** with `root-territories-1.3.2.jar`

---

## [1.4.10] — 2026-07-05

### Added
- **Default ridge spawn walls** bundled in jar (`spawnarea-refined.txt`, 160 vertices @ X -884..-816, Z -306..-284).
- Auto-seed on load when boundary file is missing, empty, or still the **legacy origin ring** (old map near 0,0).

### Removed
- Old circular ring default (~72 points near world origin).

**Deploy:** `root-spawn-1.4.10.jar` — replaces live `spawnarea-refined.txt` on first load if old ring detected.

---

## [1.4.9] — 2026-07-05

### Added
- **`protection.grief_buffer_blocks`** (default **20**) — no dig/build within N blocks outside the mapped wall line; PvP/mobs still use the exact wall polygon.

### Changed
- Spawn mapping copy: **walls** not ring; safe zone to **Y=0** documented in `/rootspawn map` hints.

**Deploy:** `root-spawn-1.4.9.jar` + handoff `root-spawn.yml`

---

## [1.4.8] — 2026-07-01

### Changed
- Chamber runtime was split to **`root-chamber`** in this release — **retired 2026-07-05**; deploy `root-spawn` only.
- `root-spawn` keeps spawn protection, ring particles, and boundary mapping (`/rootspawn map`, `import`, `build`, `reload`).

**Deploy:** `root-spawn-1.4.8.jar` only (no `root-chamber`)

---

## [1.4.7] — 2026-07-01

### Added
- **`/rootspawn map`** — click outer perimeter → `spawnarea.txt`
- **`/rootspawn import`** — waypoints → `spawnarea-refined.txt` (in-game alternative to `refine-spawn-area.py`)
- **`/rootspawn build chamber|well`** — basement footprint mapping (restored)
- **`/rootspawn stop`** and **`/rootspawn reload`**

### Changed
- Handoff boundary files cleared for fresh-world remap (`spawnarea-refined.txt`, `chamber.txt`, `well.txt`)
- `root-mapper.yml` — added `spawn`, `spawn-ring`, and `lava` areas

**Deploy:** `root-spawn-1.4.7.jar` + handoff `RootRecord/` boundary files + `root-mapper.yml`

---

## [1.4.2] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
