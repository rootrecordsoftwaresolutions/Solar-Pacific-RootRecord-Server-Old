# root-appreciation

Appreciation Tokens (sunflower item + PDC) for player rewards.

**Source:** `Plugin Building/Minecraft/plugins/root-appreciation/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.7.17] â€” 2026-07-31

### Changed
- Right-click redeem skips shop signs / chests / containers so tokens can be traded in chest shops.

**Deploy:** with **root-chestshops 1.7.14** â€” Claims **and** Towny; restart.

---

## [1.7.16] â€” 2026-07-31

### Changed
- **`/thanks give`** â€” console only (OPs / in-game blocked).

**Deploy:** `root-appreciation-1.7.16.jar` + `root-appreciation.yml` (`give-console-only`) â€” Claims **and** Towny; restart.

---

## [1.7.15] â€” 2026-07-31

### Added
- **Weighted redeem catalog** â€” 247 rewards from the power-ranking list (`appreciation-rewards.yml` in jar).
- **Right-click redeem** â€” spend 1 token for 1 roll; global broadcast of winner + prize.
- **Empty-slot gate** â€” requires 1 empty storage or offhand slot before redeem.
- **Free Lotto Ticket** reward grants Root-Gamble `/lotto` free-play credit (softdepend).
- Token lore points to `rootmc.net/thanks` (odds + tutorial). Lore refreshes on join.

### Changed
- `redeem.enabled` default **true**; `/thanks redeem [amount]` (default 1).

**Deploy:** `root-appreciation-1.7.15.jar` + updated `root-appreciation.yml` + `root-gamble-1.0.5.jar` (free-play grant) â€” Claims **and** Towny; restart. Deploy web `/thanks/` for odds page.

---

## [1.7.14] â€” 2026-07-31

### Added
- **Vote token pending queue** â€” `root_appreciation_pending` with idempotency keys (`vote:{voteRowId}`).
- **`grantOrQueue` / `grantVoteTokens`** â€” soft-SPI for Root-Play; delivers online or queues offline.
- **Join backfill** â€” scans `root_rewards_votes` and queues 1 token per historical vote, then delivers pending into inventory.

**Deploy:** `root-appreciation-1.7.14.jar` + `root-appreciation.yml` (new `pending-delivered` message) with **root-play 1.7.15** â€” Claims **and** Towny; restart. Past votes grant tokens on next login.

---

## [1.0.1] â€” 2026-07-31

### Fixed
- Chat prefix default is empty; live handoffs had `messages.prefix` set to `[Appreciation]` while `motd-bonus` also embedded the same tag (double prefix on join). Cleared prefix + stripped tag from `motd-bonus`.

**Deploy:** upload `plugins/RootMC/root-appreciation.yml` (Towny / Claims as needed); `/thanks reload` or restart. No jar bump required.

---

## [1.0.0] â€” 2026-07-28

### Added
- **Appreciation Tokens** â€” sunflower base item, custom name/lore, PDC id.
- **`/thanks`** â€” global + personal issued/redeemed/supply stats; admin `give`; `redeem` (gated off until rewards exist).
- **`/bonus`** â€” daily streak Day1â€“5 (1â€“5 tokens + 5â€“25 G); miss 48h window resets to Day 1.
- **Join MOTD reminder** when `/bonus` is available.
- **Announcer** lines for `/bonus` / `/thanks`.
- MySQL ledger + aggregates (`root_appreciation_*`).

**Deploy:** `root-appreciation-1.0.0.jar` + `root-appreciation.yml`; ensure `database.yml`; restart. Also refresh `root-announcer.yml` for new tips.
