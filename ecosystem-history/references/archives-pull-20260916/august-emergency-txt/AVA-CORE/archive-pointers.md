# Archive pointers — what moved / deleted / stayed

## Living truth

All condensed operator knowledge lives in this `AVA-CORE/` folder.

## Forensics kept

| Path | Why |
|------|-----|
| `../_forensics/telegram-DM-Alex-TRANSCRIPT.md` | Bot API cannot backfill TG history |
| `../_forensics/channels-raw/` | Discord/Slack/TG API dumps + ava-runtime state from channels status |

## Untouched (must remain)

| Path | Why |
|------|-----|
| `../Ava Laptop/AvaIvy/` | Electron / EXE runtime |
| `../Ava Laptop/Ava Ivy/AvaIvy/` | EXE kit copy if present |
| `../Ava Laptop/Start-Ava-Laptop.*`, `Stop-Ava.*` | Launchers |
| `../Ava Laptop/Ava Ivy/Start-Ava-Ivy.cmd`, `Ava Control Panel.cmd` | Launchers |
| `../Ava Laptop/Ava Ivy/data/` | Runtime state |
| `../Ava Laptop/Ava Ivy/appearance/` | Locked visuals |
| `../Ava Laptop/Ava Ivy/dream-pack/` | Kit may load |
| `../.credentials/` | Secrets |
| `../media/`, `../Minecraft-Marketing/` | Assets |
| Plugin jars, Claims zip, `Post-As-Ava.*` | Binaries / tools |

## Restored (operator request 2026-08-06)

| Path | Notes |
|------|-------|
| `../Ava-Ivy-Full-Context.md` | Restored session context dump |
| `../chatgpt improvements.txt` | Restored improvement guide + OpenAI key line |
| `.credentials/.env` → `OPENAI_API_KEY` | Key also stored in `.env` — **never delete keys** |

## Deleted after merge (duplicates absorbed into AVA-CORE)

| Former path | Absorbed into |
|-------------|----------------|
| `channels status/**` (except `_raw` + TG transcript moved to forensics) | 02, 03, 05, 06, 08 |
| `Ava Laptop/Ava Ivy/reports/` | channel dump history superseded by forensics + AVA-CORE |
| Absorbed markdown masters under Ava Ivy (bible, HOME, persona copies, goals/roadmap notes that were fully distilled) | 01–11 |

If something critical is missing, check `_forensics/` first, then restore from OneDrive version history / E:`.Ava_Ivy` live tree.
