# rootmc-shops

Player shops, `/buy`.

**Source:** `Plugin Building/Minecraft/plugins/rootmc-shops/`

---

## [1.3.69] — 2026-07-19 (Gen2 pilot)

### Added
- **Global MySQL storage** — exact item variants are organized into per-player bins with `/item deposit hand|all`, `/item withdraw`, and `/withdraw <item>`.
- **Virtual marketplace GUI** — players list stored items at a per-item chat-entered price, browse physical and virtual stock together, purchase into My Storage, and cancel their listings.
- **Safe delivery** — storage operations use row locking and exact item serialization; withdrawal overflow requires confirmation before dropping.

### Changed
- **Physical shops retained** — real chest inventory remains authoritative, while signs and shop chests open the matching unified market view.
- **Gen2 only** — no Gen1 source or handoff changes are included.

**Deploy:** upload `rootmc-shops-1.3.69.jar` and `root-admin-1.0.21.jar` to Gen2 only, remove older versions, and restart.

---

## [1.3.56] — 2026-07-02

### Fixed
- **`/buy` balance check** — tolerate sub-cent rounding when quoting and confirming purchases (`canAfford` epsilon).

**Deploy:** `rootmc-shops-1.3.56.jar` — restart.

---

## [1.3.52] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
