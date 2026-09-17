# Discord #updates — July 2026 feature posts

**Channel:** `#updates` (`1520665313631408251`)  
**Date:** 2026-07-02 (HST)

Run (after creating channels):

```powershell
cd "RootMC Workspace\Web Files\rootmc-realm-api"
node scripts/setup-discord-governance-channels.mjs --confirm
# Copy IDs into post command:
$env:CONSTITUTION_CHANNEL_ID="<id>"
$env:GOVERNANCE_CHANNEL_ID="<id>"
node scripts/post-discord-updates-2026-07-02.mjs
```

Or from workspace root:

```powershell
powershell -File "scripts\discord-post.ps1" -Channel updates -Message "..."
```

---

## Post 1 — Constitution & wiki

```
📜 **RootMC Constitution & wiki**

We published the **RootMC Constitution** — the authoritative rules for Gold, the **Server Reserve**, taxes, treasury grants, and governance.

• **Wiki:** https://rootmc.net/wiki/constitution/
• **Read-only Discord:** #constitution (pinned summary)
• **Economy how-to:** https://rootmc.net/wiki/economy/
• **Reserve dashboard:** https://rootmc.net/reserve/ · in-game `/reserve`

All automated grants debit the **Server Reserve** (never mint). **All Gold is redeemable** at the mint peg.
```

---

## Post 2 — Governance channel

```
🗳️ **Community governance on Discord**

New channel: **#governance** — **linked Minecraft accounts** can discuss and vote.

• Link first: https://rootmc.net/verify (`/link` in-game)
• `/proposal list` · `/proposal status id:<id>`
• Vote **For / Against / Abstain** on proposal threads (staff opens votes)
• **Voting power** = playtime-weighted share of 100% (+ listing-site boost from verified `/vote`) — see Constitution

Policy: https://rootmc.net/wiki/constitution/#governance-voting
```

---

## Post 3 — Server Reserve recap

```
🏦 **Server Reserve & treasury (recap)**

The reserve (`towny-server`) recycles taxes, fees, and Towny sinks — dividends, vote Gold, grants, and loans **debit the reserve**, not player wallets.

• `/reserve` · `/baltop` (no args shows reserve balance)
• Web ledger: https://rootmc.net/reserve/
• Activity Dividend: ≥20h prior HST month · 50% of pool on the 1st

Full rates: Constitution **Taxes & fees** section
```

---

## Post 4 — Map return grant

```
🗺️ **Returning players — map grant**

Fresh map launch; town banks kept. One-time **1000 G** from the reserve:

• `/link` → **https://rootmc.net/verify**
• `/rootmc claim-return` (once per account)

Eligible: joined before 2026-07-01, OR Towny resident record.

Also live: `/back`, 24h new-player grace (`/rtp`, keep inv), embassy plots **100 G**, `/buy` fix, loan cap `min_both`.
```

---

## Post 5 — Command test

```
📋 **Command testing onboarding (`/cmdtest`)**

Learn commands once, earn up to **1000 G** from the treasury (split across commands you can use).

• Reminders until each command is run · **one free test** + fee refund on paid commands
• `/cmdtest` · `/cmdtest list` · `/cmdtest report <key>` if something breaks

Requires **roothelp 1.1.0** on the live server after upload + restart.
```

---

## Post 6 — Public reachout

```
📣 **Public reachout & hourly treasury summary**

Large treasury events (grants, vote totals, Discord activity payouts) broadcast in-game and relay to **#ingame-chat** when configured.

**root-announcer** prepends an hourly line with grant/vote/Discord counts from the reserve.

Wiki hub: https://rootmc.net/wiki/ · questions in **#general** or `/feedback` in-game.
```

---

## New Discord channels

| Channel | Who can post | Purpose |
|---------|----------------|---------|
| **#constitution** | Staff/bot only (read-only) | Pinned link to wiki Constitution |
| **#governance** | **MC-linked** role | Discuss community votes · `/proposal` · vote announcements |

Setup: `node scripts/setup-discord-governance-channels.mjs --confirm`

Add to `wrangler.toml`:

- `DISCORD_ROOTMC_CONSTITUTION_CHANNEL_ID`
- `DISCORD_ROOTMC_GOVERNANCE_CHANNEL_ID`

Redeploy **rootmc-api** so proposal announcements route to `#governance`.

---

## Related docs

- `Change Logs/CONSTITUTION-2026-07-02.md`
- `Change Logs/COMMAND-TEST-2026-07-02.md`
- `Change Logs/DEPLOY-2026-07-02.md`
