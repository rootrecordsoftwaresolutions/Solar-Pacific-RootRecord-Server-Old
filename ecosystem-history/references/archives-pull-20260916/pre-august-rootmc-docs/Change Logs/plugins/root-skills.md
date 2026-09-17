# Root-Skills

## 1.0.3 â€” shared MySQL via database.yml

- MySQL pool now resolves through `RootMcDatabaseConfig` â†’ `plugins/RootMC/database.yml` (same Shockbyte DB as the suite).
- Blank / localhost `mysql.host` in `config.yml` no longer overrides the shared file (fixes memory-only boot on Claims).
- Auto-clears legacy localhost host on enable and rewrites config.
- Common: loopback hosts do not count as a plugin-owned MySQL endpoint.

**Deploy:** `root-skills-1.0.3.jar` â†’ Claims/Towny/Test; remove `1.0.2`; FileZilla + restart. Confirm `MySQL pool ready` (not memory-only).

## 1.0.2 â€” PROP-01 proportional XP climb

- Retuned `RETRO_EXPONENTIAL` so 1â€“100 actually ramps (Melee ask / governance implement_now).
- New defaults: multiplier **1.6**, exponent **2.35**, base **500** (was 0.12 / 2.05 / 2800 â€” almost flat).
- Approx `xpToNext`: L1â‰ˆ500 Â· L10â‰ˆ860 Â· L25â‰ˆ3.6k Â· L50â‰ˆ16k Â· L75â‰ˆ41k Â· L100â‰ˆ81k.
- Auto-migrates live configs that still have the exact legacy triple on enable/reload.
- Prestige / anti-farm / talents unchanged.

**Deploy:** `root-skills-1.0.2.jar` â†’ Claims/Towny/Test handoffs; remove `1.0.1`; FileZilla + restart (or `/rootskills reload` after jar swap if already live).

## 1.0.1 â€” v1 content ship

- Filled all **21** skill YAML defs (XP tables aligned toward live mcMMO Retro values where practical).
- **21** default talents (one signature talent per skill) â€” SHIFT+F loadout.
- `SkillCatalog` loads `skills/*.yml`; listeners use catalog XP (blocks / combat / action).
- Salvage XP via grindstone result clicks; PAPI: `%rootskills_power%`, `%rootskills_<skill>_level|xp|prestige%`, `%rootskills_mana%`, `%rootskills_class%`.
- Added to heartbeat / `manifest.json` publish set.
- Still **stage-only** for live cutover â€” migrate mcMMO with `/rootskills migrate`, then remove mcMMO after verify (see `CUTOVER.md`).

## 1.0.0 â€” scaffold

- Engine, MySQL schema, mcMMO migrator, Retro curve, hub GUI, mana/classes/prestige/boosters/parties stubs wired.

## [1.8.0] — 2026-08-01

### Changed
- **August suite sync** — version line rolled to `1.8.0` (`YEAR.MONTH.BUILD`: year 2026 / August / first publish of the month). Same feature set as prior jar unless noted above; suite-wide version alignment for Root-Core updater + handoffs.

---
