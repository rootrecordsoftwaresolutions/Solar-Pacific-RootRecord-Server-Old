# RootMC Discord — Town & Nation channels

Automated town/nation Discord channels for the RootMC guild (`1516108585740800042`).

## Categories

| Type | Category ID | Status |
|------|-------------|--------|
| Towns | `1516282271848726628` | **DISABLED** — category temporarily removed from guild |
| Nations | `1516283613283483749` | **DISABLED** — category temporarily removed from guild |

Private channel create/patch/delete + mayor/leader DM invites are **off** while `DISCORD_ROOTMC_TOWN_CATEGORY_ID` / `DISCORD_ROOTMC_NATION_CATEGORY_ID` are blank in wrangler/`.env` and the cron reconcile call is removed from `realm-index.ts`.

## Channels

| Channel | ID | Use |
|---------|-----|-----|
| `#general-chat` | `1516108586307158088` | Community chat; app support deep-link |
| `#ingame-chat` | `1516706598519832677` | In-game global chat bridge (RootMC ↔ RootMC bot) |
| `#bot-spam` | `1516391754625187921` | Misc automated bot posts |
| Daily summary | `1516395175780286615` | Midnight HST combined category reports |
| Town general info | `1516282373426249878` | Town founded / fallen announcements |
| Nation general info | `1516283667364974602` | Nation founded / fallen announcements |

Optional archive categories (legacy — channels are **deleted** on fall, not archived):

- `DISCORD_ROOTMC_TOWN_ARCHIVE_CATEGORY_ID` (unused)
- `DISCORD_ROOTMC_NATION_ARCHIVE_CATEGORY_ID` (unused)

## How it works

1. **RootMC plugin** reads Towny towns/nations each sync (~5 min) and POSTs to `POST /api/rootmc/towny/sync`.
2. **rootmc-api** (`api.rootmc.net`) stores snapshots in D1 (`rootmc_towny_*` tables).
3. **Private channel reconcile** (cron `*/10` + post-sync) is **disabled** while town/nation categories are removed.
4. **Founded / fallen** announcements still post to town general info (`1516282373426249878`) / nation general info (`1516283667364974602`) when those channels are configured (deduped in D1).

To re-enable private channels: recreate the Discord categories, set both category IDs in `wrangler.toml` / `.env`, restore `handleTownyDiscordReconcileCron` in `realm-index.ts` (and optional post-sync reconcile), and deploy.

## Bot permissions (when private channels are re-enabled)

- Manage Channels
- Manage Permissions (for private town/nation channels)
- Create Instant Invite
- Send Messages / Embed Links
