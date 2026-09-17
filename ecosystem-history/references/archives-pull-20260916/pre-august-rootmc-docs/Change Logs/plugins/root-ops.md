# root-ops

Staff tools, graceful restart (`/rootrestart` / `/rootstop`), announcer, area mapper.

**Source:** `Plugin Building/Minecraft/plugins/root-ops/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.7.7] â€” 2026-08-01

### Added
- **Admins cannot ban admins** â€” `/ban` and `/tempban` refuse targets who are op, have `rootadmin.ban` / Essentials ban nodes, or `group.admin` / `group.owner`.

**Deploy:** `root-ops-1.7.7.jar` â€” Claims **and** Towny; delete older `root-ops-*.jar`; Shockbyte restart.

---

## [1.7.5] â€” 2026-07-31

### Fixed
- **`/rootrestart` ClassNotFoundException** for nested `RestartCountdown$Kind` â€” moved to top-level `RestartKind` (no `$` in jar entry; safer for FileZilla/FTP uploads).

**Deploy:** `root-ops-1.7.5.jar` â€” Claims **and** Towny; delete `root-ops-1.7.4.jar`; restart from panel if needed, then `/rootrestart` works again.

---
