# Updatable values — RootMC

**Only edit this file** when vote IDs, version, or media change — then sync workspace config and search `copy/` for old URLs.

Do **not** replace paste-ready text in `copy/` with `{{tokens}}`; update real URLs in the listing docs and [`master-fields.md`](master-fields.md).

---

## Version & capacity

| Field | Current | Notes |
|-------|---------|-------|
| Minecraft version | `26.2` | Paper — use highest tag listing sites support |
| Max players | `1000` | |
| Vote reward range | `1`–`20` G | Server Reserve / treasury |

---

## Vote & listing URLs *(change when re-registering on a site)*

| Site | Listing ID / slug | Vote URL | Manage URL |
|------|-------------------|----------|------------|
| Minecraft-MP | `359724` | https://minecraft-mp.com/server/359724/vote/ | https://minecraft-mp.com/server/359724/ |
| MinecraftServers.org | `689134` | https://minecraftservers.org/vote/689134 | https://minecraftservers.org/server/689134 |
| Minecraft Server List | `521165` | https://minecraft-server-list.com/server/521165/vote/ | https://minecraft-server-list.com/server/521165/ |
| Minecraft.Buzz | `21857` | https://minecraft.buzz/vote/21857 | https://minecraft.buzz/server/21857 |
| TopMinecraftServers | `43816` | https://topminecraftservers.org/vote/43816 | https://topminecraftservers.org/server/43816 |
| MineRank | `rootmc-top-tier-economy-server` | https://www.minerank.com/rootmc-top-tier-economy-server/vote | https://www.minerank.com/rootmc-top-tier-economy-server |
| MinecraftList.org | `34144` | https://minecraftlist.org/vote/34144 | https://minecraftlist.org/server/34144 |
| Planet Minecraft | `rootmc` | https://www.planetminecraft.com/server/rootmc/vote/ | https://www.planetminecraft.com/server/rootmc/ |

---

## Votifier *(host panel — do not commit secrets to public forks)*

| Field | Current |
|-------|---------|
| Host | *(Shockbyte public IP — set locally)* |
| Port | *(NuVotifier port from server — set locally)* |

---

## Media & ops *(optional — fill when available)*

| Field | Current |
|-------|---------|
| Trailer URL | |
| Referrals page | https://rootmc.net/referrals/ |
| Banner file | *(local asset path)* |
| Staff email | *(local only)* |
| Constitution version | `2026-07-16` |
| Weekly awards | Sunday 08:00 HST |

---

## Workspace sync checklist

When any row above changes, update:

- [ ] `copy/master-fields.md`
- [ ] `copy/listings/*.md` (wired sites)
- [ ] `Plugin Building/Minecraft/plugins/root-rewards/src/main/resources/root-rewards.yml`
- [ ] `Web Files/rootmc-realm-api/src/rootmc-app-rewards.ts`
- [ ] `Web Files/rootmc-realm-api/src/rootmc-listing-sites.ts`
- [ ] `Web Files/rootmc-web/public/index.html`
- [ ] `ROOTMC-MARKETING-DEPLOYMENT.md` (vote checklist links)
