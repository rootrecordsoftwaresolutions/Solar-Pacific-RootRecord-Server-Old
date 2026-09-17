# roothelp

`/rules`, `/cmds`, `/discord`.

**Source:** `Plugin Building/Minecraft/plugins/roothelp/`

---

## [1.1.0] — 2026-07-02

### Added
- **`/cmdtest`** command onboarding — one-time pass for all players (including returning)
- Auto-detect command usage from `/cmds` catalog; reminders every 2 min until each command is run
- Up to **1000 G** total, split evenly across eligible commands (permission-filtered)
- **Fee refund** on first test use for paid commands (`/survey`, `/warp create`, `/town new`, `/shop stock`)
- **`/cmdtest try <key>`** for manual steps (e.g. shop right-click)
- **`/cmdtest report <key> [note]`** → Discord staff embed via API
- MySQL: `root_command_test_progress`, `root_command_test_done`

**Deploy:** `roothelp-1.1.0.jar` + `roothelp.yml` + **rootmc-realm-api** (`/api/rootmc/command-test-report`), restart.

---

## [1.0.14] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
