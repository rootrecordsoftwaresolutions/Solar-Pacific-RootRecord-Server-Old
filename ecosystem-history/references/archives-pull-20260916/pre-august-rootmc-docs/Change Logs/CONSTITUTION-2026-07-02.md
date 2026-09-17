# RootMC Constitution — published

**Version:** `2026-07-02`  
**Declared:** July 2, 2026 (HST)  
**Ratification deadline:** Sunday, July 5, 2026, 08:00 HST  
**Vote:** https://rootmc.net/governance/vote/?id=549cf16c  
**Canonical URL:** https://rootmc.net/wiki/constitution/

---

## What it is

The **RootMC Constitution** (version **2026-07-02**) is the authoritative rules document for the gold-backed economy, **Server Reserve**, treasury grants, redemption, net worth, taxes, and governance. Linked voters ratify this version via the Council poll before **Sunday 5 July 2026, 08:00 HST**. Player how-to guides (`/wiki/economy/`, `/wiki/player/`) link here for policy and rates.

---

## Sections (high level)

| Topic | Summary |
|-------|---------|
| **Foundations** | Closed-loop economy, 0 G start, gold-backed G, no server shop |
| **Governing body** | Staff decides how to use reserve surpluses |
| **Server Reserve** | `towny-server` treasury — inflows, outflows, `/reserve`, web dashboard |
| **Treasury grants** | Automated payouts always debit reserve (never mint) |
| **Gold redemption** | All G redeemable at mint peg; blocks → satchels/leather when inventory full |
| **Net worth** | Wallet + physical gold in chests/inventory + shop stock |
| **Activity Dividend** | 50% of prior-month pool, ≥20h HST playtime |
| **Governance voting power** | Playtime-primary shares summing to 100%; listing-site multipliers from verified `/vote` |
| **Taxes & fees** | Full rate table (transaction tax, Towny, loans, inactivity, ranks) |

---

## Discord

- **`#constitution`** — read-only channel; pinned summary + link to wiki (version **2026-07-02**, ratify by **Sun 5 Jul 08:00 HST**)
- **`#voting`** — active ratification poll `549cf16c`
- **`#updates`** — announcement posted 2026-07-02

---

## In-game / web links

- `/rules` footer → constitution + player guide
- `/reserve`, `/baltop`, `/survey` → constitution URL
- Spawn hologram (`goldecohelp`) → rootmc.net/wiki/constitution
- Reserve dashboard breadcrumb → constitution

---

## Deploy (web)

From `Web Files/rootmc-web`:

```powershell
powershell -File deploy.ps1
```

No plugin jar required for the wiki page alone.

---

## Follow-up (not in this doc)

- Weighted `/proposal` tallies on API (playtime × stake × site multiplier) — policy is live in constitution; code rollout separate.
