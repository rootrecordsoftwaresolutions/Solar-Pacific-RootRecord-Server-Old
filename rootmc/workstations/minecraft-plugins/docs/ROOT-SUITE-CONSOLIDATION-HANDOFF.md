# Root suite consolidation — host prune list

After uploading new product jars, **delete** these old jars from `plugins/` (both Towny and Claims hosts):

## Absorbed → delete
- `root-activity-*.jar` → Root-Times
- `root-spawn-*.jar`, `root-banner-*.jar`, `root-potions-*.jar`
- `rootmc-shops-*.jar`, `root-bonds-*.jar`, `root-upkeep-*.jar`, `root-loans-*.jar`, `root-tokens-*.jar` → Root-Essentials
- `root-ranks-*.jar`, `root-rewards-*.jar`, `roothelp-*.jar`, `root-road-*.jar` → Root-Play
- `root-admin-*.jar`, `root-restart-*.jar`, `root-announcer-*.jar`, `root-mapper-*.jar` → Root-Ops

## Keep (products)
- `root-core-*.jar` (1.2.8+)
- `root-times-*.jar` (1.1.0+)
- `root-essentials-*.jar` (1.6.0+)
- `root-claims-*.jar` (Claims host; optional/absent on pure Towny host)
- `root-territories-*.jar`
- `rootmc-*.jar` (not rootmc-shops)
- `root-play-*.jar`
- `root-ops-*.jar`
- `root-bluemap-r2-fix-*.jar` (Ops companion if BlueMap present; STARTUP sidecar)

## Host packs (same Root jars)
| Host folder | Land | Notes |
|---|---|---|
| `2. RootMC - Towny\` | Towny (+ optional no Claims) | Towny `/t` stays Towny's |
| `1. RootMC - Claims\` | Root-Claims | Claims registers `/t` redirects only if Towny absent |

## External (unchanged)
- Towny / TownyChat / maptowny — Towny host only
- Vault, LuckPerms, PlaceholderAPI, ProtocolLib, BlueMap, mcMMO, etc.

## Configs
Do **not** delete `plugins/RootMC/*.yml` for absorbed plugins — the new jars still read the same filenames (`root-activity.yml`, `root-spawn.yml`, `rootmc-shops.yml`, …).

## Load order
Core → Times → Essentials → Claims (Claims host) → RootMC → Territories → Play → Ops (+ BlueMap-R2 sidecar if BlueMap).
