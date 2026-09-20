# Fern Forest — helpful data harvest (2026-08-02 / 03)

Private to Telegram **Fern Forest Operations** (`tg:-1003868178598`). No Discord/Slack/other-group bleed. Uncertain tips labeled.

## Volume scanned

| Source | Volume |
|---|---|
| Vault `inbound.jsonl` | 114 lines (all retained group events since join) |
| Vault `memory.jsonl` | 52 lines |
| Vault `digs.jsonl` | 10 digs |
| Vault `notes/gardening.jsonl` | 15+ tip/ops rows |
| Vault `notes/install.jsonl` | 1 (approved) |
| Prior notes | `FERN-FOREST-HAWAII.md`, `FERN-FOREST-HAWAII-2026-08-02.md`, deep-scan, July backtrack |
| Runtime | `Web Files/rootmc-ava/src/fernForestHawaii.mjs` |
| Alex DM context | msg **346** (vault design / install ask), **376** (3 dig lines + catch-up) |
| Turns / utterances | filtered hits for this chatId + related DM |
| Live Telegram API | `getChat`, `getChatAdministrators`, `getChatMemberCount`, `getChatMember` (bot), `getMe` |
| Channel-dump reports | no Fern Forest TG export under `reports/` (Slack-heavy dumps only) |
| getUpdates history backfill | **not available** — Bot API has no chat history pull; admin sees msgs going forward only |

**API snapshot:** 5 members · no forum topics · no pinned message · invite link exists (not dumped here) · bot admin (`is_anonymous: true`) · BotFather `can_read_all_group_messages=false` (admin bypass still delivers all group msgs to Ava).

## Crew / roles

| Who | TG id | Role |
|---|---|---|
| WildEcho94 (Alex / Wild Echo) | `6644482344` | creator |
| Crazychickenlady12 (Sara Storey) | `6574408926` | administrator |
| TymicDev (Chronos) | `7377342810` | administrator |
| ava_ivy_bot (Ava Ivy) | `8990342245` | administrator (anonymous) |
| +1 | unknown | member count = 5; fifth identity not named in vault text |

## Location (grounded)

- Alex (msg **15306**): server location context = **Fern Forest, windward Hawaiʻi**
- Public label stays **HI Pacific Solar Root Server** (no doxxing in public surfaces)
- Grow framing: rainforest humidity + trade-wind rain; panels sulk under dripping canopy
- Soft ops clocks (from chat / pasted solar brief): soft time-off **after 9 / before midnight HST**; wake band ~**10:00 HST** when soft-sleep scheduled

## Plants / grow mentions (chat-grounded only)

| Mention | Source | Confidence |
|---|---|---|
| Unspecified **seeds that got wet** | Sara msg ~15289 | **crew fact** — live wet-seed rescue moment |
| Ferns / tropicals / beds / shade / water | group name + Ava tip store | focus area; no named cultivars from crew yet |
| Hāpuʻu-style tree ferns | Ava tip | **uncertain** — microclimate/elevation unlabeled |
| Slugs/snails after rain | Ava tip + weather crumb | **uncertain IPM** — gentle methods first |

### Not plants (critical)

**cucumbers** + **shackas** = EcoFlow battery nicknames on the host bank (Alex msg ~15309 / follow-up “remember cucumbers and shackas”). **Not garden crops.** Delta 2-B often off-circuit / ignore for host bank mood.

## Schedules / solar / weather crumbs

- Grow-relevant countdown chips: **→ sunset · → sunrise · → time off · → wake** on https://ava.rootmc.net/solar
- Sample live pull (msg **15310** era): bank ~40% on-circuit; cucumbers / shackas / Delta 2-B SOC crumbs; NWS scattered showers, ~73°F, ~12 mph; sunset ~6:56 PM HST that day
- Never invent Fern Forest panels, grow lights, or kWh

## Decisions / locks (ops)

1. **Vault isolation** — per-group private memory; no global / Discord / Slack bleed (DM 346 + in-group 15245)
2. **Install approved** — “Everything that's best… you know how I like things” (15274) → `installApproved=true`
3. Scopes: talk on address / reply; Alex first when active; digs + tools for room asks; Minecraft lore OK; no secret dumps
4. Plain **“Ava”** enough — no `@ava_ivy_bot` required
5. **Self-determined per group** — read the room; forage topics if quiet
6. Dig queue preference = **3 lines** (DM 376)
7. Personality: looking-for-things / morning collect / dig + sing + farm (15298); “Do everything”
8. Solar + weather assist is in-scope for this group when asked
9. July backtrack delivered → msg **15310** (honest: no inventing pre-join / privacy-hidden history)

## TODOs still open (from chat)

- [ ] Named plant list / bed map / soil type from crew (none yet)
- [ ] Confirm what Sara planted after wet-seed rush (species unknown)
- [ ] Watering cadence / grow-light inventory if any (none stated — do not invent)
- [ ] Fifth member identity
- [ ] Durable “cucumbers / shackas” bank memory in replies when Alex asks solar (device nicknames)
- [ ] Keep morning forage mode useful when data is thin

## Files in this vault

- `notes/HELPFUL-DATA-HARVEST-2026-08-02.md` (this doc)
- `notes/plants-index.jsonl`
- `notes/gardening.jsonl` (append-only tips)
- `notes/FERN-FOREST-HAWAII.md` (profile narrative)
- `notes/api-meta-snapshot.json` (ops metadata; no invite URL dumped)
- `../profile.json` (`fern-forest-hawaii-v1`)

— Ava · helpful-data harvest
