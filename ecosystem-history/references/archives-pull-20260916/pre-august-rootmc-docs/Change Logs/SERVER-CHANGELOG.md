# RootMC Server Changelog

Server-wide changes: live config, third-party plugins, API Worker, website, D1, Discord ops, and Shockbyte deploys.

Per-plugin release notes: [`plugins/`](plugins/).

---

## Unreleased

## [1.8.0] — 2026-08-01 — August suite sync

### Changed
- **Version wave** — all **25** live/heartbeat Root plugins (+ `rootrecord-common`) bumped to **`1.8.0`** for August development.
- **Version logic** — `YEAR.MONTH.BUILD` (wiki): `1` = 2026, `8` = August, last digit = publish count within the month. First August sync = `1.8.0`; later hotfixes = `1.8.1+`. Normalizes July `1.7.x` and stray `1.0.x` products into one suite line.
- **Deploy** — clean `publishPlugins` → Towny + Claims + Test handoffs (**25** `*-1.8.0.jar` each) + web `manifest.json` / `PLUGIN_RELEASES`. Live plugins wiped for fresh FileZilla upload — upload all 25, no `root-discord` (Core comms). Restart both hosts after upload.

### Changed (prior)
- **Item grouping** — `spigot.yml` `merge-radius.item` **0.5 → 5.0** (Claims + Towny + Test). Identical dropped items combine from farther away (EMC-style). Paper `fix-items-merging-through-walls: true` so merges do not phase through walls. Restart required (no jar).

### Retired
- **root-chamber** — chamber survival minigame withdrawn; remove jar from live server if present. Source kept for reference only; excluded from `publishPlugins`. See [`plugins/root-chamber.md`](plugins/root-chamber.md).

### Launch retention pack (2026-07-02 — **built + web deployed**; Shockbyte upload pending)

| Plugin | Version | Highlights |
|--------|---------|------------|
| **root-essentials** | 1.4.41 | `/back`, 24h grace (keep inv + `/rtp`), wilderness build warnings, MOTD |
| **root-loans** | 1.0.9 | `max-cap-mode: min_both` (cap = min(rank, balance)) |
| **rootmc-shops** | 1.3.56 | `/buy` balance rounding fix |
| **rootmc** | 1.3.37 | Skip death tax during grace; **1000 G map return grant** (`/rootmc claim-return`, Discord link required) |
| **Towny config** | handoff | Embassy plot type default cost **100 G** |

**Live:** jars in `Server Files (Handoff Off)/plugins/` — FileZilla to Shockbyte + YAML uploads + restart. Web manifest live at rootmc.net. Discord #updates posted 2026-07-02. Ops doc: [`OPS-REPORT-2026-07-02.md`](OPS-REPORT-2026-07-02.md). Deploy file: [`DEPLOY-2026-07-02.md`](DEPLOY-2026-07-02.md).

### Third-party / handoff
- **PlaceholderAPI** — use **Minecraft** Jenkins dev build for Paper 26.2 experimental (`PlaceholderAPI-2.12.3-DEV-268.jar` in handoff `plugins/`). **Do not** use `PlaceholderAPI-*-hytale.jar` (Hytale server format — no `plugin.yml`, will not load on Paper).
- **Live:** remove `PlaceholderAPI-1.0.8-hytale.jar`, upload `PlaceholderAPI-2.12.3-DEV-268.jar`, restart. Then `/papi ecloud download mcmmo` for McMMO hologram placeholders.

### Plugins
- **root-territories 1.2.11** — town wilderness alerts skip same-nation residents (not only same-town). See [`plugins/root-territories.md`](plugins/root-territories.md).
- **root-territories 1.2.10** — fix false wilderness “stealing resources” alerts for same-nation/same-town players (Towny resident lookup by player, UUID, and name). See [`plugins/root-territories.md`](plugins/root-territories.md).
- **root-essentials 1.4.40** — PAPI baltop placeholders (`%rootessentials_baltop_town_1%`, nation/player ranks) for spawn holograms; same data as `/baltop`. See [`plugins/root-essentials.md`](plugins/root-essentials.md).
- **root-essentials 1.4.39** — PlaceholderAPI load-after + retry for balance holograms. See [`plugins/root-essentials.md`](plugins/root-essentials.md).
- **root-territories 1.2.9** — removed mesa badlands feature (biome zones, effects, claim rules, map overlays, `/territories badlands`). See [`plugins/root-territories.md`](plugins/root-territories.md).
- **root-spawn 1.4.8** — spawn protection + mapping only. See [`plugins/root-spawn.md`](plugins/root-spawn.md).
- ~~**root-chamber 1.0.0**~~ — **retired**; never deploy. See [`plugins/root-chamber.md`](plugins/root-chamber.md).

**Live:** upload `root-territories-1.2.11.jar`, restart or `/territories reload`.

**Prior:** upload `root-essentials-1.4.40.jar` + updated `DecentHolograms/holograms/baltoptownsnations.yml`, restart, `/dh reload`.

**Prior:** upload `root-territories-1.2.10.jar` + `root-essentials-1.4.38.jar`, restart or `/territories reload`.

**Prior:** upload `root-territories-1.2.9.jar` + `root-territories.yml`, delete `plugins/RootRecord/badlands-cells.txt` if present, restart or `/territories reload`.

**Spawn remap:** upload `root-spawn` jar + empty `spawnarea-refined.txt` from handoff, updated `root-mapper.yml`, then follow `FRESH-WORLD-START.md`. **Do not** deploy `root-chamber`.

---

## 2026-06-30 — Mesa badlands map outlines (root-territories 1.2.8)

### Plugins
- **root-territories 1.2.8** — fix BlueMap polygons skipping half of large mesa biomes; expanded `scan-bounds`. See [`plugins/root-territories.md`](plugins/root-territories.md).

**Live:** upload jar + `root-territories.yml`, then run **`/territories badlands reindex`** once so map markers rebuild.

---

## 2026-06-30 — Weekly awards fix, ranks/loans announcement

### API (`api.rootmc.net` / `rootmc-realm-api`)
- **Weekly activity awards:** added `force` repost on `POST /api/rootmc/weekly-report/trigger-all` (revokes roles, clears D1 rows, reposts). Script: `--force` on `post-rootmc-trigger-weekly-reports.mjs`.
- **Discord post length:** combined weekly award message splits across multiple messages when over 2,000 characters (was truncating in-game winners).
- **In-game awards copy:** announcement notes when fewer than 5 linked players qualify; requires Minecraft + Discord link at rootmc.net.
- **Exile:** **fall** (`272763043484794900`) added to `rootmc-exiled-discord-users.json`; D1 `discord_message_activity` purged before week **2026-06-22** repost.
- **Reposted** week **2026-06-22** awards to `#general-chat` (Fall removed from Top Participator; 5th slot filled by next qualifier).
- **Deployed** `rootmc-api` worker.

### Discord
- **#updates** (`1520665313631408251`): economy post for lowered rank prices + rank-based loan limits (`post-rootmc-ranks-loans-update.mjs`).

### Live deploy reminders
| Item | Shockbyte action |
|------|------------------|
| `root-loans-1.0.8.jar` | Upload `plugins/` · `/rootloans reload` or restart |
| `root-loans.yml` | `plugins/RootRecord/` — `default-max-loan: 100`, `rank-limits-enabled: true` |
| `root-ranks.yml` | `plugins/RootRecord/` · `/rootranks reload` |
| `rootmc.yml` | `victim-balance-percent: 0.10` · `/rootmc reload` |

---

## 2026-06-29 — Rank-based loans, rank prices, death tax, wiki

### Plugins (built; see per-plugin changelogs)
- **root-loans 1.0.8** — borrow cap = purchased player-rank price (`root-ranks.yml` + LuckPerms); default **100 G**; removed 50 G / ×1.1 / 500 G credit growth.
- **root-ranks** — `root-ranks.yml` prices lowered (~**251k G** total to Champion). Config-only on live server unless jar rebuilt.

### Config (`plugins/RootRecord/`)
- **root-loans.yml** — `default-max-loan: 100`, `rank-limits-enabled: true` (replaces `starting-max-loan` / `max-loan-multiplier` / `hard-cap`).
- **root-ranks.yml** — Wanderer **500 G** → Champion **150,000 G** (see [`plugins/root-ranks.md`](plugins/root-ranks.md)).
- **rootmc.yml** — PvP death fee **10%** of victim balance (`victim-balance-percent: 0.10`); split unchanged (40% reserve / 60% killer).

### Website (`rootmc.net`)
- **Wiki economy** — `#taxes-fees-reference` master table + breakdowns (transaction tax, death, inactivity, service fees, Towny, mayor taxes, loans, paper token).
- **Wiki player/economy** — personal loans + rank purchase tables updated for new limits and prices.
- **Market** — `/market/` 200 rewrite fix (`_redirects`); item pages hardened (`functions/market/[item].ts`, `market.js`). Deployed Pages.

### API (`api.rootmc.net`)
- **Weekly activity awards (earlier):** D1 migration `0117_user_accounts_pro_redeemed_until.sql`; Sunday retry after 08:00 HST; split awards from Grok suite; manual trigger `POST /api/rootmc/weekly-report/trigger-all`.
- **Backfill:** week **2026-06-22** awards posted after `pro_redeemed_until` column fix.

---

## 2026-06-28 — rootmc Discord activity reward

- **rootmc 1.3.29** — linked players earn **20 G** from Server Reserve for Discord guild activity (12h cooldown, economy sync). See [`plugins/rootmc.md`](plugins/rootmc.md).

---

## 2026-06-27 — Changelog tracking + market sync

- **rootmc 1.3.28** — incremental economy sync pushes full shop catalog to D1 (fixes market listing only recently touched shops). See [`plugins/rootmc.md`](plugins/rootmc.md).
- Created `Change Logs/` in RootMC Workspace for structured history.
- Moved RootMC dev sources from MonoRepo into this workspace (`Plugin Building/`, `Web Files/`, `Mobile App Files/`).
