# Dig — Telegram group deep scan (2026-08-02 / 03)

## Group

| Field | Value |
|---|---|
| Chat id | `-1003868178598` (`tg:-1003868178598`) |
| Title | **Fern Forest Operations** |
| Type | supergroup |
| Vault | `data/telegram/groups/-1003868178598/` |
| Profile | `fern-forest-hawaii-v1` / Fern Forest Hawaii |
| Install | **approved** (`installApproved=true` by Alex `6644482344`) |

## Members / admins (getChatAdministrators)

| Who | Id | Role |
|---|---|---|
| WildEcho94 (Alex) | `6644482344` | creator |
| Crazychickenlady12 (Sara) | `6574408926` | administrator |
| TymicDev (Chronos) | `7377342810` | administrator |
| ava_ivy_bot | `8990342245` | administrator (`is_anonymous: true`) |

No forum topics on this chat (`has_topics_enabled` false on bot; chat has no `is_forum` flag in getChat).

## Privacy

- BotFather privacy still `can_read_all_group_messages=false` globally.
- **Admin bypass:** as group admin she receives **all** messages in this group (Telegram rule). Poller still only *engages* on @tag / name / reply-to-bot / command.
- Join: `my_chat_member:administrator` logged (join path fixed earlier).

## Alex DM re-read

- **#346** — design groups: per-group private vault, no global bleed, talk in-group, **ask Alex inside the group before install**.
- **#363** — solar countdown timers (DM dig; separate).
- **#373** — “I tried but you didn’t msg” (pre-tag; group id not yet visible).

## Traffic (what Ava saw)

Addressed/ingest: Hi Ava; Sara welcome; `@ava_ivy_bot please` / `try now` / `hbn?`; Sara `hi Ava`; Alex “don’t bleed… don’t fkn fix it”; `Np ava take your time`.

Also vaulted (listen-only, not engaged): CPU chatter, shy jokes, paste of DM solar countdown thread into the group.

## Bleed root cause (quiet fix)

Pasted DM reply contained `https://ava.rootmc.net/solar`. Poller `\bava\b` matched the **hostname** → treated paste as @Ava → replied in-group with DM context.

Patches (live after restart):

- `stripUrlsForNameMatch` in `recommend.mjs` / poller address check
- Install ask on **first Alex engage** (not only install/vault keywords)
- Pipeline: no blanket “all telegram = addressed”; use `telegram.addressed` / reply-to-bot

Alex said don’t loud-fix in-group — apology already sent; no further in-group dig theater.

## Actions this pass

1. Soft in-group reply + install ask → msg **15273**
2. DM Alex group id + status → msg **379**
3. Marked `installAskSent` on vault meta
4. Ava restart for URL-bleed + install-ask patches
5. Alex greenlit: “Everything that's best… you know how I like things @ava_ivy_bot” (15274)
6. Install **approved** + lock reply → msg **15291**
7. Expanded `looksLikeInstallGo` for best/full-scope phrasing
8. Phase catch-up `phase-telegram-group-deep-scan`

## Install scopes (locked)

Per Alex “everything that's best” / active-here priority:
- private vault this group only
- talk on @tag / reply
- Alex first when active
- digs + tool-building for room asks
- Minecraft lore OK
- no secret dumps / no cross-surface bleed

## July backtrack (closed)

- Ask (15258): *“More on this kind of stuff Ava, then backtrack conversations, provide a July summary if can please”* → Alex later *“Yes please ava”*
- Earlier pipeline attempt failed (`message to be replied not found` on 15280)
- Delivered vault-honest backtrack + changelog July scroll → msg **15310** (reply to 15258)
- Phase: `phase-fern-forest-july-backtrack`

— Ava
