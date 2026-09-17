# root-chestshops

Player chest shops + optional virtual storage marketplace.

**Source:** `Plugin Building/Minecraft/plugins/root-chestshops/`

---

## [1.7.14] — 2026-07-31

### Added
- **Appreciation Tokens** — shop item key `APPRECIATION_TOKEN` (distinct from plain sunflower); virtual bins merge tokens across issue-id variants.

**Deploy:** `root-chestshops-1.7.14.jar` + `root-appreciation-1.7.17.jar` — Claims **and** Towny; restart.

---

## [1.8.0] — 2026-07-28

### Added
- **Virtual My Storage** — `/item` deposit/withdraw (MySQL bins via Root-Core `database.yml`).
- **Chestless sell listings** — list from storage GUI at a chat price; cancel returns stock to bin.
- **Market merge** — Root-Market 1.8.0 shows virtual + chest quotes/listings; click virtual buys into My Storage.

### Notes
- Requires MySQL configured; if DB is down, virtual features disable and chest shops keep working.
- Tables auto-created: `{prefix}player_item_bin`, `{prefix}virtual_listing`.

**Deploy:** upload `root-chestshops-1.8.0.jar` + `root-market-1.8.0.jar` + updated yml; delete older jars; restart.
