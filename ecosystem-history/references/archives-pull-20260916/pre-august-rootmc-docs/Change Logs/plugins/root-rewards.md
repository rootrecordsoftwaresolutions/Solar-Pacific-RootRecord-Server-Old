# root-rewards

Playtime rewards / votes (now shipped inside **Root-Play**).

**Source (live):** `Plugin Building/Minecraft/plugins/root-play/` (`rootrewards` package)

---

## [1.7.15] — 2026-07-31 (via root-play)

### Added
- **`vote.appreciation-tokens: 1`** — each confirmed NuVotifier vote grants 1 Appreciation Token (idempotent per vote row). Offline votes queue and deliver on next login; historical votes backfill on join.

**Deploy:** `root-play-1.7.15.jar` + `root-appreciation-1.7.14.jar` + `root-rewards.yml` (`appreciation-tokens: 1`) — Claims **and** Towny; restart.

---

## [1.0.12] — 2026-07-02

### Added
- Hourly vote reminder for online players who have not voted in 24+ hours (`vote.reminder-*` config, `messages.vote-reminder`).

## [1.0.9] — 2026-06-28

### Fixed
- Vote payout chat message shows only gold credited to wallet; when the full reward sweeps to a loan, root-loans sends the `income-sweep` notice instead.

**Deploy:** with `root-essentials-1.4.28.jar`.

## [1.0.8] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
