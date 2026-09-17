# rootmc

Core sync, Towny snapshot, economy, Discord bridge, heartbeat.

**Source:** `Plugin Building/Minecraft/plugins/rootmc/`

---

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
## [1.7.7] â€” 2026-07-27

### Fixed
- **Cross-chat backlog dump** â€” peer prime now seeks to `MAX(id)` (not first 100 rows). Cold `last-id=0` no longer cycles historical `[T]`/`[C]` chat after restart. Peer pull window 15m â†’ 2m.

## [1.7.6] â€” 2026-07-27

### Fixed
- **Discord activity News** â€” stacked `discord_activity` transfers in one apply batch announce once with the **total** Gold (e.g. 3Ã—5 G â†’ â€œearned 15 Gâ€), not a flat 5 G.

## [1.3.88] â€” 2026-07-19

### Fixed
- **Cross-server source labels** â€” messages relayed from the opposite server now show `G1` or `G2` in the source position instead of `Discord | G1/G2`; actual Discord-user messages still show `Discord`.

**Deploy:** upload `rootmc-1.3.88.jar` to both generations, remove `rootmc-1.3.87.jar`, and restart both servers.

---

## [1.3.87] â€” 2026-07-18

### Changed
- **MySQL reporting source** â€” physical Gold, host metrics, item census, shops, economy, Towny, mcMMO, and playtime are persisted/read through MySQL + Hyperdrive instead of repeated Worker snapshot POSTs.
- **Operational-only cloud sync** â€” joins and Minecraft-day rollover retain linking, payouts, governance, and in-game actions without uploading complete reporting snapshots.
- **Reporting schema** â€” RootMC now ensures prefixed MySQL tables for item census history, physical-Gold storage, host metrics, and server status.

**Deploy:** deploy `rootmc-api` Hyperdrive reads first; then upload `rootmc-1.3.87.jar`, `root-iteminfo-0.1.3.jar`, and `root-bonds-1.0.37.jar` to both generations, remove older versions, and restart.

---

## [1.3.86] â€” 2026-07-18

### Fixed
- **Single Discord Gateway session** â€” removed duplicate bridge initialization during startup, which immediately shut down the first JDA scheduler and produced `RejectedExecutionException`.

**Deploy:** first enable Discord Developer Portal **Message Content Intent**, then upload `rootmc-1.3.86.jar` to both generations, remove older `rootmc-*.jar` files (not `rootmc-shops-*`), and restart.

---

## [1.3.85] â€” 2026-07-18

### Fixed
- **Discord bridge diagnostics** â€” startup now verifies the configured guild/channel and the bot's View Channel, Send Messages, and Embed Links permissions, with an explicit console error when delivery cannot work.
- **Gen 2 version continuity** â€” Gen 2 remains on the shared `1.3.x` release line.

**Deploy:** upload `rootmc-1.3.85.jar`, `plugins/RootMC/rootmc.yml`, and `plugins/RootMC/cloud.yml` from each generation's handoff; remove older `rootmc-*.jar` files (not `rootmc-shops-*`); restart and confirm `Direct Discord bridge ready` in each console.

---

## [1.3.84] â€” 2026-07-18

### Changed
- **Direct Discord bridge** â€” Paper now connects to the shared RootMC Discord channel through JDA instead of polling `rootmc-api` / `rootmc-api-g2`.
- **Bidirectional Gen bridge** â€” G1/G2-tagged player chat crosses between servers through Discord; human Discord messages broadcast to both servers exactly once.
- **Linked-player gate** â€” only Discord members with role `1516396491973984256` can send messages into Minecraft.
- **Safety** â€” bounded retry queue, no allowed mentions, channel/guild filtering, bot-loop markers, and clean Gateway shutdown.

**Deploy:** enable the Discord Developer Portal **Message Content Intent**; place the existing bot token only in each host's `plugins/RootMC/cloud.yml`; build/upload `rootmc-1.3.84.jar` for both generations; upload each handoff's `rootmc.yml` + `cloud.yml`; remove older same-plugin jars; restart Gen 1, verify, then restart Gen 2. Keep `cross-server-chat.enabled: false` and `cross-server.return-home: false`.

---

## [1.3.61] â€” 2026-07-10

### Fixed
- **`/link` app sign-in code** â€” linked players now always receive a fresh 6-character code for RootMC mobile 2FA (previously showed â€œalready linkedâ€ with no code).

**Deploy:** `rootmc-1.3.61.jar` â€” upload to Shockbyte, remove older `rootmc-*.jar`, restart.

---

## [1.3.53] â€” 2026-07-10

### Added
- **Host metrics minute rollups** â€” 1s CPU/RAM/disk samples, 60s POST to `api.rootmc.net` for `/health/` and hourly server specs in Discord.

**Deploy:** `rootmc-1.3.53.jar` â€” upload to Shockbyte, remove older `rootmc-*.jar`, restart.

---

## [1.3.46] â€” 2026-07-06

### Fixed
- **Economy sync NOTE_BURN** â€” shaded `TreasuryLedgerType` now includes `NOTE_BURN` (matches root-essentials debt-payoff / donation burns). Stops `Economy sync failed: No enum constant â€¦ NOTE_BURN` on player join.

**Deploy:** `rootmc-1.3.46.jar` â€” restart (remove older `rootmc-*.jar`).

---

## [1.3.42] â€” 2026-07-02

### Changed
- **Map return grant pre-claimed list** â€” `map-return-grant.pre-claimed` in `rootmc.yml` seeds MySQL on startup. **Alexrs94** and **ZuppaFredda** marked (received equivalent **1000 G** via town resettlement at map launch).

**Deploy:** `rootmc-1.3.42.jar` + `rootmc.yml`, restart â€” or run `scripts/mark-map-return-preclaimed.py` against live MySQL immediately.

---

## [1.3.39] â€” 2026-07-02

### Fixed
- **Map return grant eligibility** â€” removed erroneous â‰¥30 min playtime shortcut. Eligible: first join before `map-launch-cutoff`, **or** Towny resident record (Discord link still required).

**Deploy:** `rootmc-1.3.39.jar` â€” restart.

---

## [1.3.38] â€” 2026-07-02

### Added
- **Public reachout** â€” treasury grants (Discord rewards, map return, votes, etc.) broadcast in-game and relay to **#ingame-chat** when not personal/shop trades.
- **Hourly announcer summary** â€” merged last-hour totals (grants, votes, Discord rewards) injected into **root-announcer** rotation.
- **Discord first-message reward** â€” one-time **20 G** when a linked player sends their first guild message (API: `discord_first_message` transfer source).

**Deploy:** `rootmc-1.3.38.jar` + `root-announcer-1.0.7.jar` + `rootmc.yml` (`public-reachout`, `discord-chat.relay-reachout`) + **rootmc-realm-api** (first-message reward). Optional: `root-rewards` / `root-admin` jars for vote hourly totals + staff broadcast relay.

---

## [1.3.37] â€” 2026-07-02

### Added
- **Map return grant** â€” returning players (**first join before 1 Jul 2026 HST**, any playtime) can claim **1000 G** once from the server treasury via **`/rootmc claim-return`**. Requires Discord link (`/link` â†’ https://rootmc.net/verify/).

**Deploy:** `rootmc-1.3.37.jar` + `rootmc.yml` (`map-return-grant` section + messages), restart. Creates `root_map_return_grants` MySQL table on first claim.

---

## [1.3.36] â€” 2026-07-02

### Changed
- **PvP death tax** â€” skipped during Root-Essentials **24h new-player grace** (bridge via `RootMcNewPlayerGrace`).

**Deploy:** `rootmc-1.3.36.jar` â€” restart (with `root-essentials-1.4.41`).

---

## [1.3.35] â€” 2026-07-02

### Added
- **PAPI placeholders** for holograms without eCloud mcmmo/Statistic expansions: `%rootmc_playtime_<name>%`, `%rootmc_mcmmo_power_<name>%`, `%rootmc_mob_kills_<name>%`, `%rootmc_deaths_<name>%` (viewer variants: `%rootmc_mcmmo_power%`, etc.).

**Deploy:** `rootmc-1.3.35.jar` + updated `DecentHolograms/holograms/adminlist.yml` and `baltopplayersmcommo.yml`, restart, `/dh reload`.

## [1.3.32] â€” 2026-06-29

### Changed
- **PvP death fee default:** `victim-balance-percent: 0.10` (**10%** of victim balance) in `rootmc.yml` and `DeathTreasuryListener` fallback. Split unchanged: **40%** Server Reserve / **60%** killer.
- **Wiki / holograms:** economy guide, player guide, `rules.yml` hologram updated to 10%.

**Deploy:** `rootmc.yml` on Shockbyte â†’ `/rootmc reload` (jar optional if only YAML changed on live server).

## [1.3.29] â€” 2026-06-28

### Added
- **Discord activity reward:** linked players who send any message in the RootMC Discord guild earn **20 G** from the Server Reserve (12-hour cooldown). Gold is queued via the API and applied on the next economy sync; one global broadcast per login session (`messages.discord-activity-reward-broadcast` in `rootmc.yml`).

**Deploy:** `rootmc-1.3.29.jar` + **rootmc-realm-api** (activity sync queues rewards). Restart Paper after jar upload.

## [1.3.28] â€” 2026-06-27

### Fixed
- Incremental economy sync (`legacy-bulk-sync: false`) now includes all shops from `shops.yml` on each push, so [rootmc.net/market](https://rootmc.net/market) lists the full catalog instead of only shops touched since last restart.

## [1.3.27] â€” 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
