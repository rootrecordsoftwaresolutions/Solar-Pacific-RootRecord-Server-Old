# Block Notes — dev update (v1.0.8)

**Block Notes** (package `com.rootrecord.rootmc`) is Root Record’s native Android companion for Minecraft — offline-first notes for bases, farms, redstone, coords, and build plans. Same Kotlin + Jetpack Compose stack as Kīlauea Alerts.

This is a **one-time progress post** since the first “Minecraft Notes / RootMC” teaser. A lot has landed.

---

## What Block Notes is

Your **second brain for Minecraft worlds** — on your phone, **no account required** for core use. Notes and worlds live in **Room** on-device. Optional **Root Record sign-in** unlocks Pro/Lifetime perks and cloud-backed features.

**Display name:** Block Notes  
**Products page:** https://rootrecord.online/products

---

## Shipped since the last update

### Notes & worlds
- Markdown editor — preview, pin, tags, swipe-to-delete, trash restore, related-note links
- Multiple **worlds** — seeds, game version, server address, active-world tracking
- **Timeline** — project log per world
- **Search**, gallery, templates
- Local timestamps on entries (device timezone)

### Coordinates & maps
- Save & label coords; **home-screen widget** for quick add
- **Drag to reorder** and **delete** coordinate lists
- **World map** with three modes:
  - **Grid** — pinch/pan X/Z map from your saved coords (works **without** server seed or Dynmap — great for EarthMC and other servers that hide the seed)
  - **Live** — optional Dynmap/BlueMap URL per world
  - **Chunkbase** — optional when you know the seed

### Build planner & reference
- Materials list, obtained toggles, progress %
- Bundled **reference** — blocks, items, mobs, enchantments, potions, villager trades
- Full **legacy block/item ID** list (grahamedgecombe) bundled for lookup offline

### Cloud (optional) — `rootrecord-api-rootmc`
Worker is **live:** https://rootrecord-api-rootmc.rootrecord.workers.dev/

- In-app **feedback** → this channel
- **Root Record sign-in** / membership sync
- **World AI reports** — Grok analyzes your notes, coords, build plans, timeline, and media for a world (Free: 1/day · Pro/Lifetime: 100/month)
- App session notifications to RootMC

### Monetization & polish
- AdMob banner + interstitial (hidden for Pro/Lifetime)
- Play Console **upload key** registered; release builds via `bump-and-build-release.bat`
- Current build in repo: **v1.0.8** (code 8) — listing assets and Play upload in progress

---

## Still on the roadmap

Voice notes · sketch canvas · camera/OCR · Google Drive backup · PDF export · expanded widget set · deeper Realm/social ties

---

## We want your input

If you play **survival, technical, or server Minecraft** (EarthMC, SMPs, singleplayer grind — all fair game):

- What would make Block Notes a **daily driver** for you?
- Any **must-have** missing from coords, maps, or build planning?

Reply in this channel or use **in-app feedback** (More → Send feedback) when signed in.

Built by **Root Record** · https://rootrecord.online/
