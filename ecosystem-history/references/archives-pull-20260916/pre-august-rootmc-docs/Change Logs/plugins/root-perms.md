# root-perms

Custom MySQL permissions (replaces LuckPerms for RootMC).

**Source:** `Plugin Building/Minecraft/plugins/root-perms/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.7.16] â€” 2026-07-31

### Added
- **`dev` staff group** (priority 110, after admin) â€” inherits admin; seeded with `*` (all OP / plugin permissions). Online holders get vanilla OP for Paper commands. **No chat prefix.**
- Seed YAML now supports **`inherits:`** and **`nodes:`** on `seed-groups` (written to MySQL on load/reload).
- Staff ladder inherits: helper â†’ moderator â†’ admin â†’ **dev** â†’ owner.
- Chat / essentials recognize `group.dev` (and still `group.developer`).
- StaffDeop skips Dev/Owner/`*` so OP is not stripped from that rank.

**Deploy:** `root-perms-1.7.16.jar` + `root-perms.yml` on Claims **and** Towny; restart (or `/rootperms reload` then rejoin). Assign with `/rootperms user <name> addgroup dev`.

---
