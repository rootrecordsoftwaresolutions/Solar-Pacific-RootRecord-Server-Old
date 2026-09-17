# AdSense Discord off · economy hourly

**2026-09-05:** Operator asked to stop AdSense boot/any Discord reports and limit economy Discord to once per hour.

**Change:**
- Removed AdSense/AdMob boot tasks from origin lifespan.
- `ADSENSE_POST_DISCORD` / `ADMOB_POST_DISCORD` default **off** — EOD still writes md under reports, no Discord.
- `player-economy-report` interval **60m** (was 30m). Day board note `1h`.

**Re-enable Discord ads:** set env `ADSENSE_POST_DISCORD=1` (and AdMob twin) + recycle.
