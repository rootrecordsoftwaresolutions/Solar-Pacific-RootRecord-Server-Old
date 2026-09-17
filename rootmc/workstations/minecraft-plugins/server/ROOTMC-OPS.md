# RootMC admin model (LuckPerms-first)

RootMC staff permissions are managed in **LuckPerms**, not `ops.json`.

## Policy

- Keep `ops.json` to owner/host break-glass accounts only.
- Assign staff on the **staff** track — never vanilla OP.
- Donor billing still maps: `pro_unlocked` → **pro** | `life_member` → **lifetime**.

## Rank tracks

| Track | Groups (low → high) |
|-------|---------------------|
| **player** | default (Explorer) → wanderer → settler → pioneer → citizen → veteran → elite → champion |
| **donor** | default → supporter → patron → pro → benefactor → lifetime → founder |
| **staff** | helper → moderator → admin → developer → owner |

Highest **weight** prefix wins in chat (e.g. Pro donor + Veteran player shows `[Pro]`).

Source of truth: `server/host-handoff/config-templates/luckperms-setup.commands`

## Staff assignment

Console on the live server:

```text
lp user <name> parent add moderator
lp user <name> parent add admin
lp sync
```

Or promote on the staff track:

```text
lp user <name> promote staff
lp sync
```

**Moderator** — kick, mute, tempban, tp, vanish, socialspy  
**Admin** — ban, invsee, eco, jail, grants (inherits moderator)

## Donor / player ranks

```text
lp user <name> parent add pro
lp user <name> parent add lifetime
lp user <name> promote donor
lp user <name> promote player
lp sync
```

## Purchasable player ranks (Root-Ranks plugin)

Players buy **one tier at a time** on the **player** track with gold:

```text
/rank          — current rank + next price
/rank list     — all tiers and prices
/rank buy      — purchase next tier (aliases: /rankup, /ranks)
```

Config: `plugins/RootMC/root-ranks.yml` (total ~**251k G** to Champion).  
Gold sinks to Server Reserve when treasury is available.

## Apply from repo (MySQL)

```powershell
python Minecraft/server/scripts/apply-rootmc-luckperms-db.py --dry-run
python Minecraft/server/scripts/apply-rootmc-luckperms-db.py
```

Then `/lp sync` on the server (or full restart).

## If older OP entries exist

- Remove non-owner staff from `ops.json`.
- Restart Paper.
- Re-assign with LuckPerms staff groups.
