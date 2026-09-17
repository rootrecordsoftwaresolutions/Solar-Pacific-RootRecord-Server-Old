# Kīlauea Alerts — upcoming release (v1.0.45)

**Kīlauea Alerts** (`com.rootrecord.kilauea`) is Root Record’s native Android app for Big Island volcano status, USGS notices, earthquakes, NWS alerts, live feeds, and weather — with an offline-first cache and a web companion at **https://rootrecord.online**.

This is a **one-time preview** of what’s shipping in the next Play Store build (**1.0.45**, version code 45). If you’re already on **1.0.44**, most of the heavy lifting landed there; **1.0.45** polishes delivery, Discord integration, and monetization.

---

## What Kīlauea Alerts is

Your **pocket volcano dashboard** for Hawaiʻi Island — built for residents, visitors, and anyone tracking Kīlauea during active eruptive periods.

- **USGS volcano notices** — free push alerts when HVO posts a new notice
- **Earthquakes** — Big Island seismic activity near the summit
- **NWS alerts** — weather and civil alerts (Pro)
- **Live feeds** — curated USGS YouTube camera streams
- **Weather** — Root Record dashboard presets for Hilo, Volcano, Kona, and more
- **AI Analysis** — Grok-powered situation summaries for members (Free: limited · Pro/Lifetime: higher quota)

**Display name:** Kīlauea Alerts  
**Google Play:** https://play.google.com/store/apps/details?id=com.rootrecord.kilauea  
**Web companion:** https://rootrecord.online  
**Products page:** https://rootrecord.online/products

---

## Background volcano alerts (1.0.44 foundation, refined in 1.0.45)

The server now checks USGS HANS every **10 minutes** and sends **FCM push notifications** when a new Kīlauea notice posts — even if the app is closed.

- Alerts can arrive with the app in the background or killed
- WorkManager still polls on a schedule, at boot, and when you return to the app (backup path)
- **No duplicate tray notifications** while you’re already in the app (UI still refreshes)
- Notice IDs now match server/FCM so the same USGS notice won’t ping twice after you open the app
- Push marks notices as seen so open-app polling doesn’t re-fire them

**After you update:** open **More** → turn on USGS volcano notifications and grant permission on Android 13+ if asked.

---

## Situation briefing — major-event page

When something big is happening, we can publish a **server-managed Situation page** (D1-backed) without waiting for a store release.

- New **Situation** screen — full briefing text, updated remotely via ops
- **Home banner** when an event is active (dismissible until the next update)
- Deep link from push notifications opens Situation directly
- Same API powers the web companion dashboard

This is the “one page to read during an eruption” — curated context on top of raw USGS feeds.

---

## Volcano notification controls (More tab)

Fine-grained controls for urgent eruptive periods:

- **Break Do Not Disturb / Bedtime** — urgent channel can bypass DND when enabled
- **Alarm-style sound** — optional loud alert for orange/red or eruptive notices
- **Elevated-only filter** — only orange/red or eruptive notices; routine updates stay quiet
- **Shortcut to Android urgent channel settings** — one tap to system notification controls

USGS volcano alerts stay **free**. NWS + earthquake pushes remain **Pro**.

---

## AI Analysis (members)

The **AI Analysis** tab shows Grok-generated situation summaries triggered by volcano notices, significant earthquakes, NWS alerts, and scheduled cron analysis.

- Latest report + previous report history
- Free tier: limited daily quota · Pro/Lifetime: higher monthly quota
- Reports post to Discord automatically (see below)
- Web companion shows the same reports at kilauea.rootrecord.info

---

## Discord — Root Record Global Updater (`kilauea` category)

**New in 1.0.45:** automated Kīlauea feeds now fan out through the **Root Record Global Updater** to any Discord server subscribed to the **`kilauea`** category.

If you run a community server and want USGS quakes + AI reports without wiring webhooks yourself:

1. Add the **Root Record Global Updater** bot to your server
2. **`/root channel set`** — pick your updates channel
3. **`/root categories set kilauea`** — subscribe to Kīlauea feeds only
4. **`/root categories list`** — see all category ids
5. **`/root help`** — full setup guide

**What posts on `kilauea`:**
- USGS Big Island earthquake embeds (M2.0+ near Kīlauea, deduped per server)
- Kīlauea AI analysis reports (volcano, seismic, NWS triggers)
- Manual `/kilauea report` output from subscribed native Kīlauea bot servers

**What does NOT mix in:** ROOTS economy, mints, or internal ops feeds — those categories are blocked from multi-server fan-out by design.

Bot invite and docs: https://rootrecord.online/products

---

## Live feeds & API updates

On the **`rootrecord-api-kilauea`** worker side:

- Fresher volcano hero status on Home (~2 minute cache TTL)
- Feedback from the app → Discord when configured
- FCM re-registers after you sign in with Root Record
- Password manager autofill on sign-in fields

---

## What’s new in 1.0.45 specifically

Building on the 1.0.44 alert-delivery overhaul:

- **Interstitial ads** — occasional full-screen ads on tab navigation for free users (Pro/Lifetime: ad-free, same as banner policy); tuned to roughly every 50 navigations so it stays out of the way during urgent monitoring
- **Global Updater fan-out** — AI reports and USGS quake embeds reach `/root` subscribers on the `kilauea` category automatically
- **Version bump** — 1.0.45 (code 45) staged for Play Console upload

---

## Web companion (same release window)

**https://rootrecord.online** — dashboard charts, eruption summary boards, AI reports, auth, and autofill tweaks aligned with the Android build.

No install required; great for desktop monitoring during an active period.

---

## After you update — quick checklist

1. Update on Google Play when **1.0.45** appears
2. **More** → enable USGS volcano notifications (+ Android 13+ permission)
3. For overnight alerts: review **DND bypass** + **alarm sound**, then check the urgent channel in system settings
4. Sign in with your Root Record email if you’re Pro (unlocks NWS/eq pushes + AI quota)
5. Discord server admins: add Global Updater + **`/root categories set kilauea`** if you want automated feeds

---

## We want your input

If you live on Hawaiʻi Island, visit Volcano, or follow Kīlauea from afar:

- Are the **background pushes** arriving when you expect them?
- Is the **Situation page** useful during major events?
- What would make the **AI summaries** more actionable?

Reply in this channel or use **in-app feedback** (More → Send feedback) when signed in.

Mahalo to everyone who reported duplicate notifications, login friction, and overnight alert gaps — that feedback shaped 1.0.44 and this release.

Built by **Root Record Software Solutions** · https://rootrecord.online/
