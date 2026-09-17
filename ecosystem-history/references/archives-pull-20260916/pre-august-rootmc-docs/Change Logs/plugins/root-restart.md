# root-restart

`/rootrestart`, daily midnight HST restart.

**Source:** `Plugin Building/Minecraft/plugins/root-restart/`

---

## [1.0.4] — 2026-07-07

**Feature:** `/rootrestart` and countdown warnings relay to Discord **#ingame-chat** (same path as `/broadcast` reachouts). Toggle with `discord.relay: false` in `root-restart.yml`.

**Deploy:** `root-restart-1.0.4.jar` + optional `root-restart.yml` update.

---

## [1.0.3] — 2026-07-07

**Fix:** Midnight restart shut down but never came back on Shockbyte.

- Live `spigot.yml` had `restart-script: ./start.sh` (file does not exist on Shockbyte).
- Plugin now **auto-patches** `spigot.yml` to `./plugins/RootRecord/restart-helper.sh` on enable.
- `restart-helper.sh` logs to `restart.log` in the server root for diagnosis.

**Deploy:** upload `spigot.yml` + `root-restart-1.0.3.jar` from handoff, restart once from Shockbyte panel.

**Fallback:** Shockbyte Panel → Tasks → **Send Restart** on midnight HST if in-game restart still fails.

---

## [1.0.2] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
