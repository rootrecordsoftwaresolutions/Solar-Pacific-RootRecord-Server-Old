# Open items (as of ~2026-08-05 / failover dump)

## Product / code

- Root-Skills proportional XP curve (PROP-01) math done → still **waiting_restart**
- Melee's "Ava's progress" daily channel request — logged, not created
- Older @Ava asks noted: CPU private DMs at 60%+, pronouns, trust system (reply only if Alex wants)
- External-drive install is the main speed bottleneck — **SSD move pending**
- GitHub push still needs host **PAT**
- Quiet / note-keeper era is intentional until operator flips voice back

## Host / continuity

- OptiPlex Ubuntu primary brain unreachable — laptop failover temporary
- Dream-path keys blanked — do not depend on them
- Confirm single Ava tree (no dual supervisors) whenever moving hosts
- Do not wipe Windows until `E:\MIGRATION-READY.txt` exists

## Operator UX

- Control Panel ↔ `Ava Ivy.exe` ↔ brain `:8787` honesty — never false dream-dark when Root Server is up
- Rewrite/send UI must not hang on dream path when Cursor is available
- Quiet by default (no mass notify energy)

## Ava wiki / status home (started 2026-08-05 night)

- **Wiki:** `https://rootrecord.info/ava/` — full atlas of everything Ava touches (RootMC + Root Record)
- **Live board:** `https://rootrecord.info/ava/status` (replaces sole reliance on `ava.rootmc.net`)
- Source on OptiPlex: `/home/ava-core/ava/workstations/projects/rootrecord-ava/`
- Tomorrow note: `/home/ava-core/ava/docs/TOMORROW-2026-08-06-AVA-WIKI.md`
- Follow-ups: verify Worker vs Pages route, link from rootrecord homepage, optional 302 from `ava.rootmc.net` → `/ava/status`

## Logging harden (done 2026-08-05 night)

- Canonical handoff: `Server Handoffs/Ava Ivy` → symlink `/home/ava-core/ava`
- `ava-ivy` + `ava-local-api` under systemd with journal stdout/err
- `ops.jsonl` + leveled `actions.jsonl`; cronRunner + local-api wired
- JSONL rotate → `data/logs/archive/*.gz`; SQLite `data/logs/index.sqlite`
- Daily TG error digest (`errorDigest.mjs`); patches on self-restart
- Docs: `/home/ava-core/ava/docs/LOGGING.md` · query: `node scripts/log-query.mjs 24`

## Finance / goals (see also 11-goals-independence.md)

- Financial advisor lane active (portfolio assist, not player Gold)
- X social lead (@RootMCNews + @RootRecord) — human veto; secrets never in Discord
- Hardware wishlist funded from Ava slice: Samsung 990 PRO (~$1k trigger), stretch RTX 5090 laptop
