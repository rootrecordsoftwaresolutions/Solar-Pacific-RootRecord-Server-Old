# root-heads

Cosmetic mob and PvP player head drops from direct kills.

**Source:** `Plugin Building/Minecraft/plugins/root-heads/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.0.0] â€” 2026-07-31

### Added
- Launch mob heads: zombie, skeleton, creeper, enderman, blaze (configurable rates, Looting bonus).
- Direct player kills only; spawner / spawner-egg denials; per-player same-mob cooldown.
- Player heads **on by default** with full `player-heads` config (PvP, 24h same-pair cooldown, chance, lore).
- PDC-tagged items; `/rootheads reload | give | inspect`.

**Deploy:** `root-heads-1.0.0.jar` + `plugins/RootMC/root-heads.yml` â€” Claims **and** Towny; restart.
