# root-iteminfo

World item census and `/info`.

**Source:** `Plugin Building/Minecraft/plugins/root-iteminfo/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [0.1.4] â€” 2026-07-18

### Fixed
- Debounced chunk-load scans until loading settles, delayed join scans, and enforced a 30-second trigger gap.
- Enforced `scan.max-chunks-per-pass`, prioritizing chunks nearest online players.
- Prevented empty startup snapshots and limited changed MySQL replacements to once every 120 seconds.

---

## [0.1.3] â€” 2026-07-18

### Changed
- Changed census persistence from immediate Worker POSTs to deduplicated asynchronous MySQL snapshots through RootMC's shared pool.
- Preserved current and HST daily census rows for Hyperdrive-backed resource history.
