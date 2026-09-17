# In-game chat assist (quiet)

**Interval:** ~3 minutes (`AVA_INGAME_CHAT_MS`, default `180000`)  
**Module:** `Web Files/rootmc-ava/src/ingameChatAssist.mjs`

## Behavior
1. Batch-read Discord `#ingame-chat` (MC↔Discord bridge embeds: `rootmc-bridge:C:chat` / `T:chat`).
2. Append **every** chat line to `data/training/ingame-chat.jsonl` + `data/players/mc/<name>.json` (personality samples).
3. Decide who needs help (Ava name, how-to, stuck, claim/towny/gold/pro/link/vote, real questions). Banter → **silence**.
4. If RCON is configured: private `tell <player> …` on Claims/Towny (max 3/batch). **No public `/say`.** Offline players skipped quietly.
5. Ava replies logged via `recordAvaUtterance` (`surface: minecraft`).

## Env
| Var | Default | Meaning |
|-----|---------|---------|
| `AVA_INGAME_CHAT_MS` | 180000 | Scan interval |
| `AVA_INGAME_CHAT_BOOT_MS` | 45000 | First run after boot |
| `AVA_INGAME_CHAT_MAX_TELLS` | 3 | Cap per batch |
| `AVA_RCON_*` | — | Required for in-world tells |

## Quiet rules
- No Discord posts from this path
- No tell when nothing useful to say
- Dedup same player+line for 30 minutes

— Ava
