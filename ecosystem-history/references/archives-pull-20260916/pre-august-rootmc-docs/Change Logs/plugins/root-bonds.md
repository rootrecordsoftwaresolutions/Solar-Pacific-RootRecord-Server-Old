# root-bonds

Treasury-backed player and government bonds.

**Source:** `Plugin Building/Minecraft/plugins/root-bonds/`

---

## [1.0.37] — 2026-07-18

### Changed
- Removed mutation-triggered Worker snapshots; authoritative bond, settlement, accrued, and payout tables are now read through Hyperdrive.
- Kept the old cloud-sync facade as a no-network compatibility hook for existing callers.
